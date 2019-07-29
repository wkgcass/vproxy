package vproxy.util.ringbuffer;

import vproxy.util.LogType;
import vproxy.util.Logger;
import vproxy.util.RingBuffer;
import vproxy.util.RingBufferETHandler;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Deque;
import java.util.LinkedList;

/**
 * the ring buffer which contains SSLEngine<br>
 * this buffer is for sending data:<br>
 * store plain bytes into this buffer<br>
 * and will be converted to encrypted bytes
 * which will be wrote to channels<br>
 * <br>
 * NOTE: storage/writableET is proxied to/from the plain buffer
 */
public class SSLWrapRingBuffer extends AbstractRingBuffer implements RingBuffer {
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
    private final SSLEngine engine;
    private final ReadableHandler readableHandler = new ReadableHandler();
    private final Deque<ByteBufferRingBuffer> intermediateBuffers = new LinkedList<>();
    private ByteBuffer temporaryBuffer = null;
    private boolean triggerReadable = false;

    SSLWrapRingBuffer(ByteBufferRingBuffer plainBytesBuffer,
                      SSLEngine engine) {
        this.plainBufferForApp = plainBytesBuffer;
        this.engine = engine;

        this.encryptedBufferForOutput = RingBuffer.allocateDirect(plainBytesBuffer.capacity());

        // we add a handler to the plain buffer
        plainBufferForApp.addHandler(readableHandler);

        // do init first few bytes
        init();
    }

    // wrap the first bytes for handshake or data
    // this may start the net flow to begin
    private void init() {
        // for the client, it should send the first handshaking bytes or some data bytes
        if (engine.getUseClientMode()) {
            generalWrap();
        }
    }

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

    void generalWrap() {
        if (isOperating()) {
            return; // should not call the method when it's operating
        }
        setOperating(true);
        try {
            _generalWrap();
        } finally {
            if (triggerReadable) {
                triggerReadable = false;
                triggerReadable();
            }
            setOperating(false);
        }
    }

    private void _generalWrap() {
        // first try to flush intermediate buffers into the output buffer
        while (!intermediateBuffers.isEmpty()) {
            ByteBufferRingBuffer buffer = intermediateBuffers.peekFirst();
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
                intermediateBuffers.pollFirst();
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
            if (intermediateBufferCap() > MAX_INTERMEDIATE_BUFFER_CAPACITY) {
                break; // should not run the operation when capacity reaches the limit
            }
            try {
                // here, we should not check whether the plain buffer is empty or now
                // because when handshaking, the plain buffer can be empty but the connection
                // should still send handshaking data
                boolean[] errored = {false};
                plainBufferForApp.operateOnByteBufferWriteOut(Integer.MAX_VALUE, bufferPlain -> {
                    final int positionBeforeHandling = bufferPlain.position();

                    ByteBuffer bufferEncrypted = getTemporaryBuffer(engine.getSession().getPacketBufferSize());
                    SSLEngineResult result;
                    try {
                        result = engine.wrap(bufferPlain, bufferEncrypted);
                    } catch (SSLException e) {
                        Logger.error(LogType.SSL_ERROR, "got error when wrapping", e);
                        errored[0] = true;
                        return;
                    }

                    assert Logger.lowLevelDebug("wrap: " + result);
                    if (result.getStatus() == SSLEngineResult.Status.CLOSED) {
                        Logger.shouldNotHappen("the wrapping returned CLOSED");
                        errored[0] = true;
                        return;
                    } else if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                        // reset the position first in case it's changed
                        bufferPlain.position(positionBeforeHandling);

                        assert Logger.lowLevelDebug("buffer overflow, so make a bigger buffer and try again");
                        bufferEncrypted = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
                        try {
                            result = engine.wrap(bufferPlain, bufferEncrypted);
                        } catch (SSLException e) {
                            Logger.error(LogType.SSL_ERROR, "got error when wrapping", e);
                            errored[0] = true;
                            return;
                        }
                        assert Logger.lowLevelDebug("wrap2: " + result);
                    } else if (result.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                        assert Logger.lowLevelDebug("buffer underflow, waiting for more data");
                        return;
                    }
                    if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                        Logger.error(LogType.SSL_ERROR, "still getting BUFFER_OVERFLOW after retry");
                        errored[0] = true;
                        return;
                    }
                    if (bufferEncrypted.position() != 0) {
                        intermediateBuffers.addLast(SimpleRingBuffer.wrap(bufferEncrypted.flip()));
                        temporaryBuffer = null;
                    }
                    if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                        assert result.getStatus() == SSLEngineResult.Status.OK;
                    } else {
                        wrapHandshake(result);
                    }
                });
                if (errored[0]) {
                    return; // end the process if errored
                }
            } catch (IOException e) {
                // it's memory operation, should not happen
                Logger.shouldNotHappen("got exception when wrapping", e);
            }
        } while (false); // use do-while to implement goto

        // check whether something should be handled
        // and recursively call the method to make sure everything is done
        if ((!intermediateBuffers.isEmpty() && encryptedBufferForOutput.free() != 0)) {
            _generalWrap();
        }
    }

    private void wrapHandshake(SSLEngineResult result) {
        assert Logger.lowLevelDebug("wrapHandshake: " + result);

        SSLEngineResult.HandshakeStatus status = result.getHandshakeStatus();
        if (status == SSLEngineResult.HandshakeStatus.FINISHED) {
            assert Logger.lowLevelDebug("handshake finished");
            return;
        }
        if (status == SSLEngineResult.HandshakeStatus.NEED_TASK) {
            Logger.shouldNotHappen("ssl engine returns NEED_TASK when wrapping");
            return;
        }
        if (status == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
            return;
        }
        //noinspection RedundantIfStatement
        if (status == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
            assert Logger.lowLevelDebug("get need_unwrap when handshaking, waiting for more data...");
        }
    }

    @Override
    public int storeBytesFrom(ReadableByteChannel channel) throws IOException {
        // do store to the plain buffer
        return plainBufferForApp.storeBytesFrom(channel);
    }

    @Override
    public int writeTo(WritableByteChannel channel, int maxBytesToWrite) throws IOException {
        // we write encrypted data to the channel
        int bytes = encryptedBufferForOutput.writeTo(channel, maxBytesToWrite);
        generalWrap();
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
}
