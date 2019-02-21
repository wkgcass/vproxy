package net.cassite.vproxy.util.ringbuffer;

import net.cassite.vproxy.util.LogType;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.RingBuffer;

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
 * encrypted bytes will be stored into this buffer<br>
 * and will be converted to plain bytes
 * which can be retrieved by user
 */
public class SSLUnwrapRingBuffer extends AbstractRingBuffer implements RingBuffer {
    private final SimpleRingBuffer plainBufferForApp;
    private SimpleRingBuffer encryptedBufferForInput;
    private final SSLEngine engine;
    private final Consumer<Runnable> resumer;

    // will call the pair's wrap/wrapHandshake when need to send data
    private final SSLWrapRingBuffer pair;

    private boolean closed = false;

    public SSLUnwrapRingBuffer(SimpleRingBuffer plainBufferForApp,
                               SSLEngine engine,
                               Consumer<Runnable> resumer,
                               SSLWrapRingBuffer pair) {
        this.plainBufferForApp = plainBufferForApp;
        this.engine = engine;
        this.resumer = resumer;
        this.pair = pair;
        resizeForInput();
    }

    private void resizeForInput() {
        this.encryptedBufferForInput = SSLUtils.resizeFor(this.encryptedBufferForInput, engine);
    }

    @Override
    public int storeBytesFrom(ReadableByteChannel channel) throws IOException {
        if (closed) {
            return 0; // don't store anything it's already closed
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

    private void generalUnwrap() {
        if (encryptedBufferForInput.used() == 0)
            return; // no data for unwrapping
        boolean inBufferWasFull = encryptedBufferForInput.free() == 0;
        setOperating(true);
        try {
            encryptedBufferForInput.operateOnByteBufferWriteOut(Integer.MAX_VALUE,
                encryptedBuffer -> plainBufferForApp.operateOnByteBufferStoreIn(plainBuffer -> {
                    SSLEngineResult result;
                    try {
                        result = engine.unwrap(encryptedBuffer, plainBuffer);
                    } catch (SSLException e) {
                        Logger.error(LogType.SSL_ERROR, "got error when unwrapping", e);
                        return false;
                    }
                    assert Logger.lowLevelDebug("unwrap: " + result);
                    if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                        return unwrap(result);
                    } else {
                        return unwrapHandshake(result);
                    }
                }));
        } catch (IOException e) {
            // it's memory operation, should not happen
            Logger.shouldNotHappen("got exception when unwrapping", e);
        } finally {
            boolean inBufferNowNotFull = encryptedBufferForInput.free() > 0;
            if (inBufferWasFull && inBufferNowNotFull) {
                triggerWritable();
            }
            setOperating(false);
        }
    }

    private void doWhenHandshakeSucc() {
        resumer.accept(pair::generalWrap);
    }

    private boolean unwrapHandshake(SSLEngineResult result) {
        assert Logger.lowLevelDebug("unwrapHandshake: " + result);
        if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
            Logger.warn(LogType.SSL_ERROR, "BUFFER_OVERFLOW for handshake, expanding");
            generalUnwrap();
            return false;
        }

        SSLEngineResult.HandshakeStatus status = result.getHandshakeStatus();
        if (status == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            Logger.shouldNotHappen("ssl unwrap handshake got NOT_HANDSHAKING");
            return false;
        }
        if (status == SSLEngineResult.HandshakeStatus.FINISHED) {
            assert Logger.lowLevelDebug("handshake finished");
            doWhenHandshakeSucc();
            return true; // done
        }
        if (status == SSLEngineResult.HandshakeStatus.NEED_TASK) {
            assert Logger.lowLevelDebug("ssl engine returns NEED_TASK");
            new Thread(() -> {
                assert Logger.lowLevelDebug("TASK begins");
                Runnable r;
                while ((r = engine.getDelegatedTask()) != null) {
                    r.run();
                }
                assert Logger.lowLevelDebug("ssl engine returns " + engine.getHandshakeStatus() + " after task");
                if (engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
                    resumer.accept(pair::generalWrap);
                } else if (engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED) {
                    resumer.accept(this::doWhenHandshakeSucc);
                } else {
                    resumer.accept(this::generalUnwrap);
                }
            }).start();
            return true;
        }
        if (status == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
            // should call the pair to wrap
            pair.generalWrap();
        }
        return true;
    }

    private boolean unwrap(SSLEngineResult result) {
        assert Logger.lowLevelDebug("unwrap: " + result);
        SSLEngineResult.Status status = result.getStatus();
        if (status == SSLEngineResult.Status.CLOSED) {
            Logger.shouldNotHappen("the unwrapping returned CLOSED");
            return false;
        }
        if (status == SSLEngineResult.Status.OK)
            return true; // done
        if (status == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
            assert Logger.lowLevelDebug("BUFFER_UNDERFLOW, expecting more data");
            return true;
        }
        if (status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
            Logger.warn(LogType.SSL_ERROR, "BUFFER_OVERFLOW, expanding");
            resizeForInput();
            resumer.accept(this::generalUnwrap); // unwrap again
            return false;
        }
        // should have no other status when reaches here
        Logger.shouldNotHappen("should not reach here, but it's ok");
        return true;
    }

    @Override
    public int writeTo(WritableByteChannel channel, int maxBytesToWrite) throws IOException {
        throw new IOException("you should not call this method, " +
            "data should be read via the buffer you set when creating this unwrap buffer");
    }

    @Override
    public int free() {
        // whether have space to store data is determined by network input buffer
        return encryptedBufferForInput.free();
    }

    @Override
    public int used() {
        // why use app buffer
        // see comments in SSLWrapRingBuffer#capacity
        return plainBufferForApp.used();
    }

    @Override
    public int capacity() {
        // why use app buffer
        // see comments in SSLWrapRingBuffer#capacity
        return plainBufferForApp.capacity();
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public void clean() {
        plainBufferForApp.clean();
        // the input buffer is not direct
        // so does not need to be cleaned
    }

    @Override
    public void clear() {
        plainBufferForApp.clear();
    }
}
