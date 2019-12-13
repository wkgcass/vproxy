package vproxy.util.ringbuffer;

import vproxy.util.LogType;
import vproxy.util.Logger;
import vproxy.util.RingBuffer;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * the ring buffer which contains SSLEngine<br>
 * store plain bytes into this buffer<br>
 * and will be converted to encrypted bytes
 * which will be wrote to channels<br>
 * <br>
 * NOTE: storage/writableET is proxied to/from the plain buffer
 */
public class SSLWrapRingBuffer extends AbstractWrapRingBuffer implements RingBuffer {
    private final SSLEngine engine;

    SSLWrapRingBuffer(ByteBufferRingBuffer plainBytesBuffer,
                      SSLEngine engine) {
        super(plainBytesBuffer);
        this.engine = engine;

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

    @Override
    protected void handlePlainBuffer(ByteBuffer bufferPlain, boolean[] errored, IOException[] ex) {
        final int positionBeforeHandling = bufferPlain.position();

        ByteBuffer bufferEncrypted = getTemporaryBuffer(engine.getSession().getPacketBufferSize());
        SSLEngineResult result;
        try {
            result = engine.wrap(bufferPlain, bufferEncrypted);
        } catch (SSLException e) {
            Logger.error(LogType.SSL_ERROR, "got error when wrapping", e);
            errored[0] = true;
            ex[0] = e;
            return;
        }

        assert Logger.lowLevelDebug("wrap: " + result);
        if (result.getStatus() == SSLEngineResult.Status.CLOSED) {
            Logger.error(LogType.SSL_ERROR, "the wrapping returned CLOSED");
            errored[0] = true;
            ex[0] = new IOException("SSLEngine closed");
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
                ex[0] = e;
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
            recordIntermediateBuffer(bufferEncrypted.flip());
            discardTemporaryBuffer();
        }
        if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            transferring = true; // not handshaking, so we can transfer data
            assert result.getStatus() == SSLEngineResult.Status.OK;
        } else {
            wrapHandshake(result);
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
}
