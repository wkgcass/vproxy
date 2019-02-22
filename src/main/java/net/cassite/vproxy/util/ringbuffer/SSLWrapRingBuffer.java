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
import java.util.function.Consumer;

/**
 * the ring buffer which contains SSLEngine<br>
 * this buffer is for sending data:<br>
 * store plain bytes into this buffer<br>
 * and will be converted to encrypted bytes
 * which will be wrote to channels<br>
 * NOTE: this buffer will reject all operations on `storing`
 * and writableET() will never fire.
 */
public class SSLWrapRingBuffer extends AbstractRingBuffer implements RingBuffer {
    class ReadableHandler implements RingBufferETHandler {
        @Override
        public void readableET() {
            generalWrap();
        }

        @Override
        public void writableET() {
            // ignored
        }
    }

    private final SimpleRingBuffer plainBufferForApp;
    private SimpleRingBuffer encryptedBufferForOutput;
    private final SSLEngine engine;
    private final Consumer<Runnable> resumer;

    SSLWrapRingBuffer(SimpleRingBuffer plainBytesBuffer,
                      SSLEngine engine,
                      Consumer<Runnable> resumer) {
        this.plainBufferForApp = plainBytesBuffer;
        this.engine = engine;
        this.resumer = resumer;
        resizeForOutput();

        // we add a handler to the input buffer
        plainBufferForApp.addHandler(new ReadableHandler());

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

    private void resizeForOutput() {
        encryptedBufferForOutput = SSLUtils.resizeFor(encryptedBufferForOutput, engine);
    }

    void generalWrap() {
        boolean outBufWasEmpty = encryptedBufferForOutput.used() == 0;
        setOperating(true);
        try {
            plainBufferForApp.operateOnByteBufferWriteOut(Integer.MAX_VALUE,
                bufferPlain -> encryptedBufferForOutput.operateOnByteBufferStoreIn(bufferEncrypted -> {
                    SSLEngineResult result;
                    int l = bufferEncrypted.limit();
                    int m = bufferPlain.limit();
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
    }

    private void doWhenHandshakeSucc() {
        resumer.accept(this::generalWrap); // call wrap to send data (if data presents)
    }

    private boolean wrapHandshake(SSLEngineResult result) {
        assert Logger.lowLevelDebug("wrapHandshake: " + result);

        SSLEngineResult.HandshakeStatus status = result.getHandshakeStatus();
        if (status == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            Logger.shouldNotHappen("ssl wrap handshake got NOT_HANDSHAKING");
            return false;
        }
        if (status == SSLEngineResult.HandshakeStatus.FINISHED) {
            assert Logger.lowLevelDebug("handshake finished");
            doWhenHandshakeSucc();
            return true; // done
        }
        if (status == SSLEngineResult.HandshakeStatus.NEED_TASK) {
            Logger.shouldNotHappen("ssl engine returns NEED_TASK when wrapping");
            return true;
        }
        if (status == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
            resumer.accept(this::generalWrap);
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
        Logger.warn(LogType.SSL_ERROR, "BUFFER_OVERFLOW, expanding");
        resizeForOutput();
        resumer.accept(this::generalWrap); // wrap again
        return false;
    }

    @Override
    public int storeBytesFrom(ReadableByteChannel channel) throws IOException {
        throw new IOException("you should not call this method, " +
            "data should be stored via the buffer you set when creating this wrap buffer");
    }

    @Override
    public int writeTo(WritableByteChannel channel, int maxBytesToWrite) throws IOException {
        // we write encrypted data to the channel
        return encryptedBufferForOutput.writeTo(channel, maxBytesToWrite);
    }

    @Override
    public int free() {
        // why use app buffer
        // see comments in SSLWrapRingBuffer#capacity
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
}
