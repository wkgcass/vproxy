package net.cassite.vproxy.util.ringbuffer;

import net.cassite.vproxy.selector.SelectorEventLoop;
import net.cassite.vproxy.util.LogType;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.RingBuffer;
import net.cassite.vproxy.util.RingBufferETHandler;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Deque;
import java.util.LinkedList;
import java.util.function.Consumer;

/**
 * the ring buffer which contains SSLEngine<br>
 * this buffer is for sending data:<br>
 * encrypted bytes will be stored into this buffer<br>
 * and will be converted to plain bytes
 * which can be retrieved by user
 */
public class SSLUnwrapRingBuffer extends AbstractRingBuffer implements RingBuffer {
    class WritableHandler implements RingBufferETHandler {
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

    private /*might be replaced when switching*/ ByteBufferRingBuffer plainBufferForApp;
    private final SimpleRingBuffer encryptedBufferForInput;
    private final SSLEngine engine;
    private final Consumer<Runnable> resumer;
    private final WritableHandler writableHandler = new WritableHandler();
    private final Deque<ByteBufferRingBuffer> intermediateBuffers = new LinkedList<>();
    private ByteBuffer temporaryBuffer = null;
    private boolean triggerWritable = false;

    // will call the pair's wrap/wrapHandshake when need to send data
    private final SSLWrapRingBuffer pair;

    // only used when resume if resumer not specified
    private SelectorEventLoop lastLoop = null;
    private boolean closed = false;

    SSLUnwrapRingBuffer(ByteBufferRingBuffer plainBufferForApp,
                        SSLEngine engine,
                        Consumer<Runnable> resumer,
                        SSLWrapRingBuffer pair) {
        this.plainBufferForApp = plainBufferForApp;
        this.engine = engine;
        this.resumer = resumer;
        this.pair = pair;

        // we add a handler to the plain buffer
        plainBufferForApp.addHandler(writableHandler);

        this.encryptedBufferForInput = RingBuffer.allocateDirect(plainBufferForApp.capacity());
    }

    @Override
    public int storeBytesFrom(ReadableByteChannel channel) throws IOException {
        if (closed) {
            return -1; // don't store anything it's already closed
        }
        int read = encryptedBufferForInput.storeBytesFrom(channel);
        if (read == 0) {
            return 0; // maybe the buffer is full
        }
        if (read == -1) {
            assert Logger.lowLevelDebug("reading from remote return -1");
            return -1;
        }
        // got new data, let's unwrap it
        generalUnwrap();
        return read;
    }

    // -------------------
    // helper functions BEGIN
    // -------------------
    private int intermediateBufferCap() {
        int cap = 0;
        for (ByteBufferRingBuffer buf : intermediateBuffers) {
            cap += buf.capacity();
        }
        return cap;
    }

    private ByteBuffer getTemporaryBuffer(int cap) {
        if (temporaryBuffer != null && temporaryBuffer.capacity() >= cap) {
            temporaryBuffer.limit(temporaryBuffer.capacity()).position(0);
            return temporaryBuffer;
        }
        temporaryBuffer = ByteBuffer.allocate(cap);
        return temporaryBuffer;
    }

    private void doResume(Runnable r) {
        if (resumer == null && lastLoop == null) {
            Logger.fatal(LogType.IMPROPER_USE, "cannot get resumer or event loop to callback from the task");
            return; // cannot continue if no loop
        }
        if (resumer != null) {
            resumer.accept(r);
        } else {
            //noinspection ConstantConditions
            assert lastLoop != null;
            lastLoop.runOnLoop(r);
        }
    }

    private void resumeGeneralUnwrap() {
        doResume(this::generalUnwrap);
    }

    private void resumeGeneralWrap() {
        doResume(pair::generalWrap);
    }
    // -------------------
    // helper functions END
    // -------------------

    private void generalUnwrap() {
        if (isOperating()) {
            return; // should not call the method when it's operating
        }
        setOperating(true);
        try {
            _generalUnwrap();
        } finally {
            if (triggerWritable) {
                triggerWritable = false;
                triggerWritable();
            }
            setOperating(false);
        }
    }

    private void _generalUnwrap() {
        if ((intermediateBuffers.isEmpty() || plainBufferForApp.free() == 0)
            &&
            (encryptedBufferForInput.used() == 0 || intermediateBufferCap() > MAX_INTERMEDIATE_BUFFER_CAPACITY)) {
            return;
        }

        // check the intermediate buffers
        while (!intermediateBuffers.isEmpty()) {
            ByteBufferRingBuffer buf = intermediateBuffers.peekFirst();
            int wrote = 0;
            if (buf.used() != 0) {
                wrote = buf.writeTo(plainBufferForApp, Integer.MAX_VALUE);
            }
            assert Logger.lowLevelDebug("wrote " + wrote + " bytes to plain buffer");
            // remove the buffer if all data wrote
            if (buf.used() == 0) {
                intermediateBuffers.pollFirst();
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
                encryptedBufferForInput.operateOnByteBufferWriteOut(Integer.MAX_VALUE, encryptedBuffer -> {
                    final int positionBeforeHandling = encryptedBuffer.position();

                    ByteBuffer plainBuffer = getTemporaryBuffer(engine.getSession().getApplicationBufferSize());
                    SSLEngineResult result;
                    try {
                        result = engine.unwrap(encryptedBuffer, plainBuffer);
                    } catch (SSLException e) {
                        Logger.error(LogType.SSL_ERROR, "got error when unwrapping", e);
                        errored[0] = true;
                        return;
                    }
                    assert Logger.lowLevelDebug("unwrap: " + result);
                    if (result.getStatus() == SSLEngineResult.Status.CLOSED) {
                        Logger.shouldNotHappen("the unwrapping returned CLOSED");
                        errored[0] = true;
                        return;
                    } else if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                        // reset the position in case it's modified
                        encryptedBuffer.position(positionBeforeHandling);
                        Logger.shouldNotHappen("the unwrapping returned BUFFER_OVERFLOW, do retry");
                        plainBuffer = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
                        try {
                            result = engine.unwrap(encryptedBuffer, plainBuffer);
                        } catch (SSLException e) {
                            Logger.error(LogType.SSL_ERROR, "got error when unwrapping", e);
                            errored[0] = true;
                            return;
                        }
                        assert Logger.lowLevelDebug("unwrap2: " + result);
                    } else if (result.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                        assert Logger.lowLevelDebug("got BUFFER_UNDERFLOW when unwrapping, expecting: " + engine.getSession().getPacketBufferSize());
                        // manipulate the position back to the original one
                        encryptedBuffer.position(positionBeforeHandling);
                        underflow[0] = true;
                        return;
                    }
                    if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                        Logger.error(LogType.SSL_ERROR, "still getting BUFFER_OVERFLOW after retry");
                        errored[0] = true;
                        return;
                    }
                    if (plainBuffer.position() != 0) {
                        intermediateBuffers.addLast(SimpleRingBuffer.wrap(plainBuffer.flip()));
                        temporaryBuffer = null;
                    }
                    if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                        assert result.getStatus() == SSLEngineResult.Status.OK;
                    } else {
                        unwrapHandshake(result);
                    }
                });
                if (underflow[0]) {
                    if (canDefragment) {
                        encryptedBufferForInput.defragment();
                    } else {
                        assert Logger.lowLevelDebug("got underflow, but the encrypted buffer cannot defragment, maybe buffer limit to small, or data not enough yet");
                        errored[0] = true;
                    }
                }
                if (errored[0]) {
                    return; // exit if error occurred
                }
            } catch (IOException e) {
                // it's memory operation, should not happen
                Logger.shouldNotHappen("got exception when unwrapping", e);
            }
        } while (false); // use do-while to implement goto

        // finally recursively call the method to make sure everything is done
        _generalUnwrap();
    }

    private void unwrapHandshake(SSLEngineResult result) {
        assert Logger.lowLevelDebug("unwrapHandshake: " + result);

        SSLEngineResult.HandshakeStatus status = result.getHandshakeStatus();
        if (status == SSLEngineResult.HandshakeStatus.FINISHED) {
            assert Logger.lowLevelDebug("handshake finished");
            // should call the wrapper to send data (if any present)
            resumeGeneralWrap();
            return;
        }
        if (status == SSLEngineResult.HandshakeStatus.NEED_TASK) {
            assert Logger.lowLevelDebug("ssl engine returns NEED_TASK");
            if (resumer == null) {
                lastLoop = SelectorEventLoop.current();
                assert Logger.lowLevelDebug("resumer not specified, so we use the current event loop: " + lastLoop);
            }
            new Thread(() -> {
                assert Logger.lowLevelDebug("TASK begins");
                Runnable r;
                while ((r = engine.getDelegatedTask()) != null) {
                    r.run();
                }
                assert Logger.lowLevelDebug("ssl engine returns " + engine.getHandshakeStatus() + " after task");
                if (engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
                    resumeGeneralWrap();
                } else if (engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED) {
                    // when handshaking is finished
                    resumeGeneralWrap(); // we try to send data
                    resumeGeneralUnwrap(); // also, we try to read data
                } else {
                    resumeGeneralUnwrap();
                }
            }).start();
            return;
        }
        if (status == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
            // should call the pair to wrap
            resumeGeneralWrap();
            return;
        }
        assert status == SSLEngineResult.HandshakeStatus.NEED_UNWRAP;
    }

    @Override
    public int writeTo(WritableByteChannel channel, int maxBytesToWrite) throws IOException {
        // proxy the operation from plain buffer
        return plainBufferForApp.writeTo(channel, maxBytesToWrite);
    }

    @Override
    public int free() {
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
    public void close() {
        closed = true;
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
}
