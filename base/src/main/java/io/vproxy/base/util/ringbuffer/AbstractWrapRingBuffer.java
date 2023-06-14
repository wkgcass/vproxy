package io.vproxy.base.util.ringbuffer;

import io.vproxy.base.util.*;
import io.vproxy.base.util.coll.RingQueue;
import io.vproxy.vfd.ReadableByteStream;
import io.vproxy.vfd.WritableByteStream;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class AbstractWrapRingBuffer extends AbstractRingBuffer implements RingBuffer {
    // this handler is for plain data buffer
    class ReadableHandler implements RingBufferETHandler {
        @Override
        public void readableET() {
            generalWrap();
        }

        @Override
        public void writableET() {
            triggerWritable(); // proxy the event
        }
    }

    private static final int MAX_INTERMEDIATE_BUFFER_CAPACITY = 1024 * 1024; // 1M

    private /*might change when switching*/ ByteBufferRingBuffer plainBufferForApp;
    private final SimpleRingBuffer encryptedBufferForOutput;
    private final ReadableHandler readableHandler = new ReadableHandler();
    private final RingQueue<ByteBufferRingBuffer> intermediateBuffers = new RingQueue<>();
    private ByteBuffer temporaryBuffer = null;
    private boolean triggerReadable = false;
    protected boolean transferring = false;
    private IOException exceptionToThrow = null;

    public AbstractWrapRingBuffer(ByteBufferRingBuffer plainBytesBuffer) {
        this.plainBufferForApp = plainBytesBuffer;

        this.encryptedBufferForOutput = RingBuffer.allocateDirect(plainBytesBuffer.capacity());

        // we add a handler to the plain buffer
        plainBufferForApp.addHandler(readableHandler);
    }

    private void checkException() throws IOException {
        if (exceptionToThrow != null) {
            throw exceptionToThrow;
        }
    }

    protected ByteBufferRingBuffer getPlainBufferForApp() {
        return plainBufferForApp;
    }

    protected int getEncryptedBufferForOutputUsedSize() {
        return encryptedBufferForOutput.used();
    }

    protected int getEncryptedBufferForOutputCap() {
        return encryptedBufferForOutput.capacity();
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

    void generalWrap() {
        if (isOperating()) {
            assert Logger.lowLevelDebug("generalWrap is operating");
            return; // should not call the method when it's operating
        }
        do {
            assert Logger.lowLevelDebug("begin to handle generalWrap");
            if (exceptionToThrow != null) {
                assert Logger.lowLevelDebug("exit wrap loop because of exception " + exceptionToThrow);
                return;
            }
            setOperating(true);
            try {
                _generalWrap();
            } finally {
                if (triggerReadable) {
                    triggerReadable = false;
                    triggerReadable();
                }
                assert Logger.lowLevelDebug("generalWrap is not operating now");
                setOperating(false);
            }
        } while (
            (plainBufferForApp.used() != 0 || !intermediateBuffers.isEmpty())
                && encryptedBufferForOutput.used() == 0
                && transferring
            // in the triggerReadable() process, the encrypted buffer might be flushed to channel
            // but will not notify the plain buffer to write data to encrypted buffer
            //
            // when encrypted buffer still have data (used() != 0), the OP_WRITE will be added and write again
            // in this case, no need for us to re-handle here
            // but when encrypted buffer is empty (used() == 0), the OP_WRITE will be canceled
            // and if plain buffer is not empty, the plainBuffer.readableET will not be triggered when reading from channel
            // then there's no chance for the network flow to be handled
            //
            // so we should handle again here if plain buffer is not empty and encrypted buffer is empty
            //
            // also, for the similar reason, we have to give the intermediateBuffers a chance to flush into encryptedBuffer
            // because the 'generalWrap' forbids recursive calls.
            //
            // the transferring check is because of the consideration that sometimes data cannot be transferred
        );
    }

    private void _generalWrap() {
        do {
            assert Logger.lowLevelDebug("calling _generalWrap");
            // first try to flush intermediate buffers into the output buffer
            while (!intermediateBuffers.isEmpty()) {
                ByteBufferRingBuffer buffer = intermediateBuffers.peek();
                int wrote = 0;
                if (buffer.used() != 0) {
                    wrote = buffer.writeTo(encryptedBufferForOutput, Integer.MAX_VALUE);
                }
                assert Logger.lowLevelDebug("wrote " + wrote + " bytes encrypted data to the output buffer");
                if (wrote > 0) {
                    triggerReadable = true;
                }
                // remove the buffer when all data flushed
                if (buffer.used() == 0) {
                    intermediateBuffers.poll();
                }
                if (encryptedBufferForOutput.free() == 0) {
                    // break the loop when there are no space in the output buffer
                    break;
                }
            }

            // then try to read data from the plain buffer
            //noinspection ConstantConditions
            do {
                // check the intermediate capacity
                int intermediateBufferCap = intermediateBufferCap();
                if (intermediateBufferCap > MAX_INTERMEDIATE_BUFFER_CAPACITY) {
                    assert Logger.lowLevelDebug("intermediateBufferCap = " + intermediateBufferCap + " > " + MAX_INTERMEDIATE_BUFFER_CAPACITY);
                    break; // should not run the operation when capacity reaches the limit
                }
                try {
                    assert Logger.lowLevelDebug("before handling data in plain buffer");
                    // here, we should not check whether the plain buffer is empty or now
                    // because when handshaking, the plain buffer can be empty but the connection
                    // should still send handshaking data
                    boolean[] errored = {false};
                    IOException[] ex = {null};
                    plainBufferForApp.operateOnByteBufferWriteOut(Integer.MAX_VALUE,
                        bufferPlain -> handlePlainBuffer(bufferPlain, errored, ex));
                    if (ex[0] != null) {
                        assert Logger.lowLevelDebug("got exception from buffer" + ex[0]);
                        exceptionToThrow = ex[0];
                    }
                    if (errored[0]) {
                        assert Logger.lowLevelDebug("handling data in plain buffer failed");
                        return; // end the process if errored
                    }
                } catch (IOException e) {
                    // it's memory operation, should not happen
                    Logger.shouldNotHappen("got exception when wrapping", e);
                }
            } while (false); // use do-while to implement goto
        } while ((!intermediateBuffers.isEmpty() && encryptedBufferForOutput.free() != 0));
        // check whether something should be handled
        // and run a loop to make sure everything is done
    }

    abstract protected void handlePlainBuffer(ByteBufferEx buf, boolean[] errored, IOException[] ex);

    @Override
    public int storeBytesFrom(ReadableByteStream channel) throws IOException {
        checkException();
        int len = 0;
        while (true) {
            // do store to the plain buffer
            int read = plainBufferForApp.storeBytesFrom(channel);
            if (read == 0) {
                break;
            }
            if (read == -1) {
                if (len == 0) {
                    return -1;
                } else {
                    break;
                }
            }
            len += read;
        }
        return len;
    }

    @Override
    public int writeTo(WritableByteStream channel, int maxBytesToWrite) throws IOException {
        checkException();
        // we write encrypted data to the channel
        int bytes = 0;
        while (true) {
            int wrote = encryptedBufferForOutput.writeTo(channel, maxBytesToWrite);
            generalWrap();
            if (wrote == 0) {
                // try to write again, maybe generaWrap produces some bytes
                wrote = encryptedBufferForOutput.writeTo(channel, maxBytesToWrite);
                if (wrote == 0) {
                    // still no bytes wrote, break the loop
                    break;
                }
            }
            bytes += wrote;
            maxBytesToWrite -= wrote;
        }
        return bytes;
    }

    @Override
    public int free() {
        // user code may check this for writing data into the buffer
        return plainBufferForApp.free();
    }

    @Override
    public int used() {
        // whether have bytes to write is determined by network output buffer
        return encryptedBufferForOutput.used();
    }

    @Override
    public int capacity() {
        // the capacity would be exactly the same for plain and encrypted buffers
        return plainBufferForApp.capacity();
    }

    @Override
    public void clean() {
        plainBufferForApp.clean();
        encryptedBufferForOutput.clean();
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
            throw new RejectSwitchException("the new buffer capacity is not the same as the old one");

        // switch buffers and handlers
        plainBufferForApp.removeHandler(readableHandler);
        plainBufferForApp = (ByteBufferRingBuffer) buf;
        plainBufferForApp.addHandler(readableHandler);

        // try to wrap any data if presents
        generalWrap();

        return this;
    }

    @Override
    public boolean isParentOf(RingBuffer buf) {
        return buf == this || buf == plainBufferForApp;
    }
}
