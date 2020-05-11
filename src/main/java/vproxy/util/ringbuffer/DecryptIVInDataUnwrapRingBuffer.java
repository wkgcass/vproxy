package vproxy.util.ringbuffer;

import vfd.NetworkFD;
import vmirror.Mirror;
import vmirror.MirrorDataFactory;
import vproxy.util.ByteArray;
import vproxy.util.Logger;
import vproxy.util.RingBuffer;
import vproxy.util.Utils;
import vproxy.util.crypto.BlockCipherKey;
import vproxy.util.crypto.StreamingCFBCipher;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

public class DecryptIVInDataUnwrapRingBuffer extends AbstractUnwrapByteBufferRingBuffer implements RingBuffer {
    private final BlockCipherKey key;

    private int requiredIvLen;
    private byte[] iv;
    private StreamingCFBCipher cipher;

    private final MirrorDataFactory mirrorDataFactory;

    public DecryptIVInDataUnwrapRingBuffer(ByteBufferRingBuffer plainBufferForApp, BlockCipherKey key, NetworkFD fd) {
        this(plainBufferForApp, key,
            () -> {
                try {
                    return (InetSocketAddress) fd.getRemoteAddress();
                } catch (IOException e) {
                    Logger.shouldNotHappen("getting remote address of " + fd + " failed", e);
                    return Utils.bindAnyAddress();
                }
            }, () -> {
                try {
                    return (InetSocketAddress) fd.getLocalAddress();
                } catch (IOException e) {
                    Logger.shouldNotHappen("getting local address of " + fd + " failed", e);
                    return Utils.bindAnyAddress();
                }
            });
    }

    public DecryptIVInDataUnwrapRingBuffer(ByteBufferRingBuffer plainBufferForApp, BlockCipherKey key) {
        this(plainBufferForApp, key, Utils::bindAnyAddress, Utils::bindAnyAddress);
    }

    private DecryptIVInDataUnwrapRingBuffer(ByteBufferRingBuffer plainBufferForApp, BlockCipherKey key,
                                            Supplier<InetSocketAddress> srcAddrSupplier,
                                            Supplier<InetSocketAddress> dstAddrSupplier) {
        super(plainBufferForApp);
        this.key = key;
        this.requiredIvLen = key.ivLen();
        this.iv = new byte[requiredIvLen];

        this.mirrorDataFactory = new MirrorDataFactory("iv-prepend", d -> {
            InetSocketAddress src = srcAddrSupplier.get();
            InetSocketAddress dst = dstAddrSupplier.get();
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
