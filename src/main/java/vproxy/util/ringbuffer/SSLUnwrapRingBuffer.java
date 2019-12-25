package vproxy.util.ringbuffer;

import vproxy.selector.SelectorEventLoop;
import vproxy.util.LogType;
import vproxy.util.Logger;
import vproxy.util.RingBuffer;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * the ring buffer which contains SSLEngine<br>
 * this buffer is for sending data:<br>
 * encrypted bytes will be stored into this buffer<br>
 * and will be converted to plain bytes
 * which can be retrieved by user
 */
public class SSLUnwrapRingBuffer extends AbstractUnwrapByteBufferRingBuffer implements RingBuffer {
    private final SSLEngine engine;
    private final Consumer<Runnable> resumer;

    // will call the pair's wrap/wrapHandshake when need to send data
    private final SSLWrapRingBuffer pair;

    // only used when resume if resumer not specified
    private SelectorEventLoop lastLoop = null;

    SSLUnwrapRingBuffer(ByteBufferRingBuffer plainBufferForApp,
                        SSLEngine engine,
                        Consumer<Runnable> resumer,
                        SSLWrapRingBuffer pair) {
        super(plainBufferForApp);
        this.engine = engine;
        this.resumer = resumer;
        this.pair = pair;
    }

    // -------------------
    // helper functions BEGIN
    // -------------------
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

    @Override
    protected void handleEncryptedBuffer(ByteBuffer encryptedBuffer, boolean[] underflow, boolean[] errored, IOException[] ex) {
        final int positionBeforeHandling = encryptedBuffer.position();

        ByteBuffer plainBuffer = getTemporaryBuffer(engine.getSession().getApplicationBufferSize());
        SSLEngineResult result;
        try {
            result = engine.unwrap(encryptedBuffer, plainBuffer);
        } catch (SSLException e) {
            Logger.error(LogType.SSL_ERROR, "got error when unwrapping", e);
            errored[0] = true;
            ex[0] = e;
            return;
        }
        assert Logger.lowLevelDebug("unwrap: " + result);
        if (result.getStatus() == SSLEngineResult.Status.CLOSED) {
            assert Logger.lowLevelDebug("the unwrapping returned CLOSED");
            errored[0] = true;
            ex[0] = new IOException("SSLEngine closed");
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
                ex[0] = e;
                return;
            }
            assert Logger.lowLevelDebug("unwrap2: " + result);
        } else if (result.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
            // manipulate the position back to the original one
            encryptedBuffer.position(positionBeforeHandling);
            assert Logger.lowLevelDebug("got BUFFER_UNDERFLOW when unwrapping, expecting: " + engine.getSession().getPacketBufferSize() + ", the buffer has " + (encryptedBuffer.limit() - encryptedBuffer.position()));
            underflow[0] = true;
            return;
        }
        if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
            Logger.error(LogType.SSL_ERROR, "still getting BUFFER_OVERFLOW after retry");
            errored[0] = true;
            return;
        }
        if (plainBuffer.position() != 0) {
            recordIntermediateBuffer(plainBuffer.flip());
            discardTemporaryBuffer();
        }
        if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            assert result.getStatus() == SSLEngineResult.Status.OK;
        } else {
            unwrapHandshake(result);
        }
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
}
