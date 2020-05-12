package vproxy.util.ringbuffer;

import vfd.IPPort;
import vfd.NetworkFD;
import vmirror.Mirror;
import vmirror.MirrorDataFactory;
import vproxy.util.ByteArray;
import vproxy.util.Logger;
import vproxy.util.RingBuffer;
import vproxy.util.crypto.BlockCipherKey;
import vproxy.util.crypto.StreamingCFBCipher;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

public class DecryptIVInDataUnwrapRingBuffer extends AbstractUnwrapByteBufferRingBuffer implements RingBuffer {
    private final BlockCipherKey key;

    private int requiredIvLen;
    private byte[] iv;
    private StreamingCFBCipher cipher;

    private final MirrorDataFactory mirrorDataFactory;

    public DecryptIVInDataUnwrapRingBuffer(ByteBufferRingBuffer plainBufferForApp, BlockCipherKey key, NetworkFD<IPPort> fd) {
        this(plainBufferForApp, key,
            () -> {
                try {
                    return fd.getRemoteAddress();
                } catch (IOException e) {
                    Logger.shouldNotHappen("getting remote address of " + fd + " failed", e);
                    return IPPort.bindAnyAddress();
                }
            }, () -> {
                try {
                    return fd.getLocalAddress();
                } catch (IOException e) {
                    Logger.shouldNotHappen("getting local address of " + fd + " failed", e);
                    return IPPort.bindAnyAddress();
                }
            });
    }

    public DecryptIVInDataUnwrapRingBuffer(ByteBufferRingBuffer plainBufferForApp, BlockCipherKey key) {
        this(plainBufferForApp, key, IPPort::bindAnyAddress, IPPort::bindAnyAddress);
    }

    private DecryptIVInDataUnwrapRingBuffer(ByteBufferRingBuffer plainBufferForApp, BlockCipherKey key,
                                            Supplier<IPPort> srcAddrSupplier,
                                            Supplier<IPPort> dstAddrSupplier) {
        super(plainBufferForApp);
        this.key = key;
        this.requiredIvLen = key.ivLen();
        this.iv = new byte[requiredIvLen];

        this.mirrorDataFactory = new MirrorDataFactory("iv-prepend", d -> {
            IPPort src = srcAddrSupplier.get();
            IPPort dst = dstAddrSupplier.get();
            d.setSrc(src).setDst(dst);
        });
    }

    @Override
    protected void handleEncryptedBuffer(ByteBuffer buf, boolean[] underflow, boolean[] errored, IOException[] ex) {
        if (requiredIvLen != 0) {
            readIv(buf);
            if (requiredIvLen == 0) {
                buildCipher();
                readData(buf);
            }
        } else {
            readData(buf);
        }
    }

    private void readIv(ByteBuffer buf) {
        int len = requiredIvLen;
        int bufLen = buf.limit() - buf.position();
        if (len > bufLen) {
            len = bufLen;
        }
        buf.get(iv, iv.length - requiredIvLen, len);
        requiredIvLen -= len;
    }

    private void buildCipher() {
        cipher = new StreamingCFBCipher(key, false, iv);
    }

    private void mirror(byte[] bytes) {
        // build meta message
        String meta = "iv=" + ByteArray.from(iv).toHexString() +
            ";";

        mirrorDataFactory.build()
            .setMeta(meta)
            .setData(bytes)
            .mirror();
    }

    private void readData(ByteBuffer input) {
        int len = input.limit() - input.position();
        if (len == 0) {
            return;
        }
        byte[] array = new byte[len];
        input.get(array);

        byte[] ret = cipher.update(array, 0, array.length);

        if (Mirror.isEnabled()) {
            mirror(ret);
        }

        assert Logger.lowLevelDebug("decrypt " + ret.length + " bytes");
        assert Logger.lowLevelNetDebugPrintBytes(ret);
        recordIntermediateBuffer(ByteBuffer.wrap(ret));
    }
}
