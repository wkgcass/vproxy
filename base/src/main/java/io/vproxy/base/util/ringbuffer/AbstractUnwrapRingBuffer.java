package io.vproxy.base.util.ringbuffer;

import io.vproxy.base.util.*;
import io.vproxy.base.util.coll.RingQueue;
import io.vproxy.vfd.ReadableByteStream;
import io.vproxy.vfd.WritableByteStream;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class AbstractUnwrapRingBuffer extends AbstractRingBuffer {
    protected class WritableHandler implements RingBufferETHandler {
        @Override
        public void readableET() {
            triggerReadable(); // proxy the event
        }

        @Override
        public void writableET() {
            generalUnwrap();
        }
    }

    private static final int MAX_INTERMEDIATE_BUFFER_CAPACITY = 1024 * 1024; // 1M

    /*might be replaced when switching*/ ByteBufferRingBuffer plainBufferForApp;
    protected final SimpleRingBuffer encryptedBufferForInput;
    protected final WritableHandler writableHandler = new WritableHandler();
    private final RingQueue<ByteBufferRingBuffer> intermediateBuffers = new RingQueue<>();
    private ByteBuffer temporaryBuffer = null;

    private boolean triggerWritable = false;

    private IOException exceptionToThrow = null;
    private boolean transportEnded = false;
    private IOException transportException = null;

    private boolean hasPendingPlaintext() {
        return plainBufferForApp.used() != 0 || !intermediateBuffers.isEmpty();
    }

    private int transportEndResult() throws IOException {
        if (hasPendingPlaintext()) return 0;
        if (transportException != null) throw transportException;
        return -1;
    }

    public AbstractUnwrapRingBuffer(ByteBufferRingBuffer plainBufferForApp) {
        this.plainBufferForApp = plainBufferForApp;

        // we add a handler to the plain buffer
        plainBufferForApp.addHandler(writableHandler);

        this.encryptedBufferForInput = RingBuffer.allocateDirect(plainBufferForApp.capacity());
    }

    private void checkException() throws IOException {
        if (exceptionToThrow != null) {
            throw exceptionToThrow;
        }
    }

    @Override
    public int storeBytesFrom(ReadableByteStream channel) throws IOException {
        checkException();
        if (transportEnded) return transportEndResult();
        int len = 0;
        while (true) {
            int read;
            int bufferedBeforeRead = encryptedBufferForInput.used();
            try {
                read = encryptedBufferForInput.storeBytesFrom(channel);
            } catch (IOException e) {
                // Decoded bytes may still be in the app buffer and staging.
                // Reporting the reset now makes Proxy release those buffers.
                transportEnded = true;
                transportException = e;
                // A wrapped ring can commit its first contiguous read before
                // the second read fails. Decode those committed bytes too.
                len += encryptedBufferForInput.used() - bufferedBeforeRead;
                generalUnwrap();
                return len != 0 ? len : transportEndResult();
            }
            if (read == 0) {
                break; // maybe the buffer is full
            }
            if (read == -1) {
                transportEnded = true;
                assert Logger.lowLevelDebug("reading from remote return -1");
                if (len == 0) {
                    return transportEndResult();
                } else {
                    break;
                }
            }
            len += read;
            // got new data, let's unwrap it
            generalUnwrap();
        }
        return len;
    }

    // -------------------
    // helper functions BEGIN
    // -------------------
    protected ByteBufferRingBuffer getPlainBufferForApp() {
        return plainBufferForApp;
    }

    protected void recordIntermediateBuffer(ByteBuffer b) {
        intermediateBuffers.add(SimpleRingBuffer.wrap(b));
    }

    protected int intermediateBufferCap() {
        int cap = 0;
        for (ByteBufferRingBuffer buf : intermediateBuffers) {
            cap += buf.capacity();
        }
        return cap;
    }

    protected int intermediateBufferCount() {
        return intermediateBuffers.size();
    }

    /** Authenticated/decoded bytes still awaiting consumption, including staging. */
    public int pendingPlaintextBytes() {
        int bytes = plainBufferForApp.used();
        for (ByteBufferRingBuffer buf : intermediateBuffers) {
            bytes += buf.used();
        }
        return bytes;
    }

    public int pendingEncryptedBytes() {
        return encryptedBufferForInput.used();
    }

    protected ByteBuffer getTemporaryBuffer(int cap) {
        if (temporaryBuffer != null && temporaryBuffer.capacity() >= cap) {
            temporaryBuffer.limit(temporaryBuffer.capacity()).position(0);
            return temporaryBuffer;
        }
        temporaryBuffer = Utils.allocateByteBuffer(cap);
        return temporaryBuffer;
    }

    protected void discardTemporaryBuffer() {
        temporaryBuffer = null;
    }
    // -------------------
    // helper functions END
    // -------------------

    protected void generalUnwrap() {
        if (isOperating()) {
            assert Logger.lowLevelDebug("generalUnwrap is operating");
            return; // should not call the method when it's operating
        }
        if (exceptionToThrow != null) {
            assert Logger.lowLevelDebug("exit unwrap because of exception " + exceptionToThrow);
            return;
        }
        setOperating(true);
        try {
            _generalUnwrap();
        } finally {
            if (triggerWritable) {
                triggerWritable = false;
                triggerWritable();
            }
            assert Logger.lowLevelDebug("generalUnwrap is not operating now");
            setOperating(false);
        }
    }

    private void _generalUnwrap() {
        do {
            if ((intermediateBuffers.isEmpty() || plainBufferForApp.free() == 0)
                // cannot write
                && // and
                // cannot read
                (encryptedBufferForInput.used() == 0 || intermediateBufferCap() > MAX_INTERMEDIATE_BUFFER_CAPACITY)) {
                return;
            }

            // check the intermediate buffers
            while (!intermediateBuffers.isEmpty()) {
                ByteBufferRingBuffer buf = intermediateBuffers.peek();
                int wrote = 0;
                if (buf.used() != 0) {
                    wrote = buf.writeTo(plainBufferForApp, Integer.MAX_VALUE);
                }
                assert Logger.lowLevelDebug("wrote " + wrote + " bytes to plain buffer");
                // remove the buffer if all data wrote
                if (buf.used() == 0) {
                    intermediateBuffers.poll();
                    triggerWritable = true;
                }
                // break the process if no space for app buffer
                if (plainBufferForApp.free() == 0) {
                    break;
                }
            }

            // then check the input encrypted buffer
            //noinspection ConstantConditions
            do {
                try {
                    if (encryptedBufferForInput.used() == 0) {
                        break;
                    }
                    // check the intermediate capacity
                    if (intermediateBufferCap() > MAX_INTERMEDIATE_BUFFER_CAPACITY) {
                        break; // should not run the operation when capacity reaches the limit
                    }
                    boolean canDefragment = encryptedBufferForInput.canDefragment();
                    boolean[] underflow = {false};
                    boolean[] errored = {false};
                    IOException[] ex = {null};
                    encryptedBufferForInput.operateOnByteBufferWriteOut(Integer.MAX_VALUE,
                        encryptedBuffer -> handleEncryptedBuffer(encryptedBuffer, underflow, errored, ex));
                    if (underflow[0]) {
                        if (canDefragment) {
                            encryptedBufferForInput.defragment();
                        } else {
                            assert Logger.lowLevelDebug("got underflow, but the encrypted buffer cannot defragment, maybe buffer limit to small, or data not enough yet");
                            errored[0] = true;
                        }
                    }
                    if (ex[0] != null) {
                        assert Logger.lowLevelDebug("got exception from buffer" + ex[0]);
                        exceptionToThrow = ex[0];
                    }
                    if (errored[0]) {
                        return; // exit if error occurred
                    }
                } catch (IOException e) {
                    // it's memory operation, should not happen
                    Logger.shouldNotHappen("got exception when unwrapping", e);
                }
            } while (false); // use do-while to implement goto
        } while (true);
        // run a loop to make sure everything is done
    }

    abstract protected void handleEncryptedBuffer(ByteBufferEx buf, boolean[] underflow, boolean[] errored, IOException[] exception);

    @Override
    public int writeTo(WritableByteStream channel, int maxBytesToWrite) throws IOException {
        checkException();
        // proxy the operation from plain buffer
        int bytes = 0;
        while (true) {
            int wrote = plainBufferForApp.writeTo(channel, maxBytesToWrite);
            if (plainBufferForApp.used() == 0) {
                generalUnwrap();
            }
            if (wrote == 0) {
                break;
            }
            bytes += wrote;
        }
        return bytes;
    }

    @Override
    public int free() {
        if (transportEnded) {
            // Stop polling the failed/EOF fd while staging drains. Removing
            // the final staged buffer triggers writable, which re-enables
            // READ so the connection can observe the saved terminal result.
            // Keep READ enabled while only the shared plain buffer remains:
            // its final byte does not necessarily generate a writable edge.
            return intermediateBuffers.isEmpty() ? 1 : 0;
        }
        // whether have space to store data is determined by network input buffer
        return encryptedBufferForInput.free();
    }

    @Override
    public int used() {
        // user may use this to check whether the buffer still had data left
        return plainBufferForApp.used();
    }

    @Override
    public int capacity() {
        // capacity of the plain buffer and encrypted buffer are the same
        return plainBufferForApp.capacity();
    }

    @Override
    public void clean() {
        plainBufferForApp.clean();
        encryptedBufferForInput.clean();
    }

    @Override
    public void clear() {
        plainBufferForApp.clear();
    }

    @Override
    public RingBuffer switchBuffer(RingBuffer buf) throws RejectSwitchException {
        if (plainBufferForApp.used() != 0)
            throw new RejectSwitchException("the plain buffer is not empty");
        if (!(buf instanceof ByteBufferRingBuffer))
            throw new RejectSwitchException("the input is not a ByteBufferRingBuffer");
        if (buf.capacity() != plainBufferForApp.capacity())
            throw new RejectSwitchException("capacity of new buffer is not the same as the old one");

        // switch buffers and handlers
        plainBufferForApp.removeHandler(writableHandler);
        plainBufferForApp = (ByteBufferRingBuffer) buf;
        plainBufferForApp.addHandler(writableHandler);

        // try to unwrap any data if presents
        generalUnwrap();

        return this;
    }

    @Override
    public boolean isParentOf(RingBuffer buf) {
        return buf == this || buf == plainBufferForApp;
    }
}
