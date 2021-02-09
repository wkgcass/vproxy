package vproxybase.util.ringbuffer;

import vfd.IPPort;
import vfd.NetworkFD;
import vmirror.MirrorDataFactory;
import vproxybase.util.*;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * the ring buffer which contains SSLEngine<br>
 * store plain bytes into this buffer<br>
 * and will be converted to encrypted bytes
 * which will be wrote to channels<br>
 * <br>
 * NOTE: storage/writableET is proxied to/from the plain buffer
 */
public class SSLWrapRingBuffer extends AbstractWrapByteBufferRingBuffer implements RingBuffer {
    SSLEngine engine; // will be set when first bytes reaches if it's null

    private final MirrorDataFactory plainMirrorDataFactory;
    private final MirrorDataFactory encryptedMirrorDataFactory;

    // for client
    SSLWrapRingBuffer(ByteBufferRingBuffer plainBytesBuffer,
                      SSLEngine engine,
                      NetworkFD<IPPort> fd) {
        this(plainBytesBuffer, engine,
            () -> {
                try {
                    return fd.getLocalAddress();
                } catch (IOException e) {
                    Logger.shouldNotHappen("getting local address of " + fd + " failed", e);
                    return IPPort.bindAnyAddress();
                }
            }, () -> {
                try {
                    return fd.getRemoteAddress();
                } catch (IOException e) {
                    Logger.shouldNotHappen("getting remote address of " + fd + " failed", e);
                    return IPPort.bindAnyAddress();
                }
            });
    }

    // for client
    SSLWrapRingBuffer(ByteBufferRingBuffer plainBytesBuffer,
                      SSLEngine engine,
                      IPPort remote) {
        this(plainBytesBuffer, engine, IPPort::bindAnyAddress, () -> remote);
    }

    // for client
    SSLWrapRingBuffer(ByteBufferRingBuffer plainBytesBuffer,
                      SSLEngine engine,
                      Supplier<IPPort> srcAddrSupplier,
                      Supplier<IPPort> dstAddrSupplier) {
        super(plainBytesBuffer);
        this.engine = engine;

        // mirror
        plainMirrorDataFactory = new MirrorDataFactory("ssl",
            d -> {
                IPPort src = srcAddrSupplier.get();
                IPPort dst = dstAddrSupplier.get();
                d.setSrc(src).setDst(dst);
            });
        encryptedMirrorDataFactory = new MirrorDataFactory("ssl-encrypted",
            d -> {
                IPPort src = srcAddrSupplier.get();
                IPPort dst = dstAddrSupplier.get();
                d.setSrc(src).setDst(dst);
            });

        // do init first few bytes
        init();
    }

    // for server
    SSLWrapRingBuffer(ByteBufferRingBuffer plainBytesBuffer,
                      NetworkFD<IPPort> fd) {
        this(plainBytesBuffer,
            () -> {
                try {
                    return fd.getLocalAddress();
                } catch (IOException e) {
                    Logger.shouldNotHappen("getting local address of " + fd + " failed", e);
                    return IPPort.bindAnyAddress();
                }
            }, () -> {
                try {
                    return fd.getRemoteAddress();
                } catch (IOException e) {
                    Logger.shouldNotHappen("getting remote address of " + fd + " failed", e);
                    return IPPort.bindAnyAddress();
                }
            });
    }

    // for server
    SSLWrapRingBuffer(ByteBufferRingBuffer plainBytesBuffer,
                      Supplier<IPPort> srcAddrSupplier,
                      Supplier<IPPort> dstAddrSupplier) {
        super(plainBytesBuffer);

        // mirror
        plainMirrorDataFactory = new MirrorDataFactory("ssl",
            d -> {
                IPPort src = srcAddrSupplier.get();
                IPPort dst = dstAddrSupplier.get();
                d.setSrc(src).setDst(dst);
            });
        encryptedMirrorDataFactory = new MirrorDataFactory("ssl-encrypted",
            d -> {
                IPPort src = srcAddrSupplier.get();
                IPPort dst = dstAddrSupplier.get();
                d.setSrc(src).setDst(dst);
            });
    }

    // wrap the first bytes for handshake or data
    // this may start the net flow to begin
    private void init() {
        // for the client, it should send the first handshaking bytes or some data bytes
        if (engine.getUseClientMode()) {
            generalWrap();
        }
    }

    private String mirrorMeta(SSLEngineResult result) {
        return "r.s=" + result.getStatus() +
            ";" +
            "e.hs=" + engine.getHandshakeStatus() +
            ";" +
            "ib=" + intermediateBufferCap() + "/" + intermediateBufferCount() +
            ";" +
            "e=" + getEncryptedBufferForOutputUsedSize() + "/" + getEncryptedBufferForOutputCap() +
            ";" +
            "seq=" + result.sequenceNumber() +
            ";";
    }

    private void mirrorPlain(ByteBufferEx plain, int posBefore, SSLEngineResult result) {
        if (plain.position() == posBefore) {
            return; // nothing wrote, so do not mirror data out
        }
        if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
            return; // nothing should have been written, but the buffer index may change
        }

        plainMirrorDataFactory.build()
            .setMeta(mirrorMeta(result))
            .setDataAfter(plain, posBefore)
            .mirror();
    }

    private void mirrorEncrypted(ByteBuffer encrypted, SSLEngineResult result) {
        if (encrypted.position() == 0) {
            return;
        }
        if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
            return;
        }

        encryptedMirrorDataFactory.build()
            .setMeta(mirrorMeta(result))
            .setDataAfter(encrypted, 0)
            .mirror();
    }

    @Override
    protected void handlePlainBuffer(ByteBufferEx bufferPlain, boolean[] errored, IOException[] ex) {
        final int positionBeforeHandling = bufferPlain.position();

        ByteBuffer bufferEncrypted = getTemporaryBuffer(engine.getSession().getPacketBufferSize());
        SSLEngineResult result;
        try {
            result = engine.wrap(bufferPlain.realBuffer(), bufferEncrypted);
        } catch (SSLException e) {
            Logger.error(LogType.SSL_ERROR, "got error when wrapping", e);
            errored[0] = true;
            ex[0] = e;
            return;
        }

        if (plainMirrorDataFactory.isEnabled()) {
            mirrorPlain(bufferPlain, positionBeforeHandling, result);
        }
        if (encryptedMirrorDataFactory.isEnabled()) {
            mirrorEncrypted(bufferEncrypted, result);
        }

        assert Logger.lowLevelDebug("wrap: " + result);
        if (result.getStatus() == SSLEngineResult.Status.CLOSED) {
            assert Logger.lowLevelDebug("the wrapping returned CLOSED");
            errored[0] = true;
            ex[0] = new IOException(Utils.SSL_ENGINE_CLOSED_MSG);
            return;
        } else if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
            // reset the position first in case it's changed
            bufferPlain.position(positionBeforeHandling);

            assert Logger.lowLevelDebug("buffer overflow, so make a bigger buffer and try again");
            bufferEncrypted = Utils.allocateByteBuffer(engine.getSession().getPacketBufferSize());
            try {
                result = engine.wrap(bufferPlain.realBuffer(), bufferEncrypted);
            } catch (SSLException e) {
                Logger.error(LogType.SSL_ERROR, "got error when wrapping", e);
                errored[0] = true;
                ex[0] = e;
                return;
            }

            if (plainMirrorDataFactory.isEnabled()) {
                mirrorPlain(bufferPlain, positionBeforeHandling, result);
            }
            if (encryptedMirrorDataFactory.isEnabled()) {
                mirrorEncrypted(bufferEncrypted, result);
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
            // simply ignore the task
            // which should be done in unwrap buffer
            assert Logger.lowLevelDebug("ssl engine returns NEED_TASK when wrapping");
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
