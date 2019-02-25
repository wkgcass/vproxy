package net.cassite.vproxy.util.ringbuffer;

import net.cassite.vproxy.util.LogType;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.RingBuffer;
import net.cassite.vproxy.util.RingBufferETHandler;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.io.IOException;
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

    private /*might change when switching*/ ByteBufferRingBuffer plainBufferForApp;
    private final SimpleRingBuffer encryptedBufferForOutput;
    private final SSLEngine engine;
    private final Deque<Runnable> deferer = new LinkedList<>();
    private final ReadableHandler readableHandler = new ReadableHandler();

    SSLWrapRingBuffer(ByteBufferRingBuffer plainBytesBuffer,
                      int outputCap,
                      SSLEngine engine) {
        this.plainBufferForApp = plainBytesBuffer;
        this.engine = engine;

        // use heap buffer for output
        // it will interact heavily with java code
        this.encryptedBufferForOutput = RingBuffer.allocate(outputCap);

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

    private void deferDefragment() {
        deferer.push(() -> {
            encryptedBufferForOutput.defragment();
            generalWrap();
        });
    }

    void generalWrap() {
        boolean outBufWasEmpty = encryptedBufferForOutput.used() == 0;
        setOperating(true);
        try {
            plainBufferForApp.operateOnByteBufferWriteOut(Integer.MAX_VALUE,
                bufferPlain -> encryptedBufferForOutput.operateOnByteBufferStoreIn(bufferEncrypted -> {
                    SSLEngineResult result;
                    try {
                        result = engine.wrap(bufferPlain, bufferEncrypted);
                    } catch (SSLException e) {
                        Logger.error(LogType.SSL_ERROR, "got error when wrapping", e);
                        return false;
                    }

                    assert Logger.lowLevelDebug("wrap: " + result);
                    if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                        return wrap(result);
                    } else {
                        return wrapHandshake(result);
                    }
                }));
        } catch (IOException e) {
            // it's memory operation, should not happen
            Logger.shouldNotHappen("got exception when wrapping", e);
        } finally {
            // then we check the buffer and try to invoke ETHandler
            boolean outBufNowNotEmpty = encryptedBufferForOutput.used() > 0;
            if (outBufWasEmpty && outBufNowNotEmpty) {
                triggerReadable();
            }
            setOperating(false);
        }

        // check deferer
        if (deferer.isEmpty())
            return; // noting to run
        assert deferer.size() == 1;
        deferer.poll().run();
    }

    private void deferGeneralWrap() {
        deferer.push(this::generalWrap);
    }

    private boolean wrapHandshake(SSLEngineResult result) {
        assert Logger.lowLevelDebug("wrapHandshake: " + result);
        if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
            Logger.warn(LogType.SSL_ERROR, "BUFFER_OVERFLOW for handshake wrap, " +
                "expecting " + engine.getSession().getPacketBufferSize());
            deferDefragment();
            return false;
        }

        SSLEngineResult.HandshakeStatus status = result.getHandshakeStatus();
        if (status == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            Logger.shouldNotHappen("ssl wrap handshake got NOT_HANDSHAKING");
            return false;
        }
        if (status == SSLEngineResult.HandshakeStatus.FINISHED) {
            assert Logger.lowLevelDebug("handshake finished");
            deferGeneralWrap();
            return true; // done
        }
        if (status == SSLEngineResult.HandshakeStatus.NEED_TASK) {
            Logger.shouldNotHappen("ssl engine returns NEED_TASK when wrapping");
            return true;
        }
        if (status == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
            deferGeneralWrap();
            return true;
        }
        // NEED_UNWRAP ignored here
        // will be triggered by network events
        return true; // return true for not eof
    }

    private boolean wrap(SSLEngineResult result) {
        assert Logger.lowLevelDebug("wrap: " + result);
        SSLEngineResult.Status status = result.getStatus();
        if (status == SSLEngineResult.Status.CLOSED) {
            // this is closed
            Logger.shouldNotHappen("ssl connection already closed");
            return false;
        }
        if (status == SSLEngineResult.Status.OK)
            return true; // done
        // BUFFER_UNDERFLOW will only happen when unwrapping, will not happen here

        // here: BUFFER_OVERFLOW
        assert status == SSLEngineResult.Status.BUFFER_OVERFLOW;
        Logger.warn(LogType.SSL_ERROR, "BUFFER_OVERFLOW in wrap, " +
            "expecting " + engine.getSession().getPacketBufferSize());
        deferDefragment(); // try to make more space
        // NOTE: if it's capacity is smaller than required, we can do nothing here
        return false;
    }

    @Override
    public int storeBytesFrom(ReadableByteChannel channel) throws IOException {
        // do store to the plain buffer
        return plainBufferForApp.storeBytesFrom(channel);
    }

    @Override
    public int writeTo(WritableByteChannel channel, int maxBytesToWrite) throws IOException {
        // we write encrypted data to the channel
        return encryptedBufferForOutput.writeTo(channel, maxBytesToWrite);
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
        // the capacity does not have any particular meaning
        //
        // for now, we return the app capacity
        // because the network output capacity is calculated and may change
        return plainBufferForApp.capacity();
    }

    @Override
    public void close() {
        // it's closed then cannot store data into this buffer
        // but this buffer rejects storage
        // so the `close()` method can simply do nothing
    }

    @Override
    public void clean() {
        // maybe the input buffer need to be cleaned
        // the output buffer is non-direct and does not need cleaning
        plainBufferForApp.clean();
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

        // switch buffers and handlers
        plainBufferForApp.removeHandler(readableHandler);
        plainBufferForApp = (ByteBufferRingBuffer) buf;
        plainBufferForApp.addHandler(readableHandler);

        // try to wrap any data if presents
        generalWrap();

        return this;
    }
}
