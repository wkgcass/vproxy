package vproxy.util.ringbuffer;

import vfd.NetworkFD;
import vmirror.Mirror;
import vmirror.MirrorDataFactory;
import vproxy.util.ByteArray;
import vproxy.util.Logger;
import vproxy.util.RingBuffer;
import vproxy.util.Utils;
import vproxy.util.crypto.BlockCipherKey;
import vproxy.util.crypto.CryptoUtils;
import vproxy.util.crypto.StreamingCFBCipher;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

public class EncryptIVInDataWrapRingBuffer extends AbstractWrapByteBufferRingBuffer implements RingBuffer {
    private boolean ivSent = false;
    private StreamingCFBCipher cipher;
    private final byte[] iv0;

    private final MirrorDataFactory mirrorDataFactory;

    public EncryptIVInDataWrapRingBuffer(ByteBufferRingBuffer plainBytesBuffer, BlockCipherKey key, NetworkFD fd) {
        this(plainBytesBuffer, key,
            () -> {
                try {
                    return (InetSocketAddress) fd.getLocalAddress();
                } catch (IOException e) {
                    Logger.shouldNotHappen("getting local address of " + fd + " failed", e);
                    return Utils.bindAnyAddress();
                }
            }, () -> {
                try {
                    return (InetSocketAddress) fd.getRemoteAddress();
                } catch (IOException e) {
                    Logger.shouldNotHappen("getting remote address of " + fd + " failed", e);
                    return Utils.bindAnyAddress();
                }
            });
    }

    private EncryptIVInDataWrapRingBuffer(ByteBufferRingBuffer plainBytesBuffer, BlockCipherKey key,
                                          Supplier<InetSocketAddress> localAddrSupplier,
                                          Supplier<InetSocketAddress> remoteAddrSupplier) {
        this(plainBytesBuffer, key, null, localAddrSupplier, remoteAddrSupplier);
        transferring = true; // we can transfer data at any time
    }

    public EncryptIVInDataWrapRingBuffer(ByteBufferRingBuffer plainBytesBuffer, BlockCipherKey key, byte[] iv) {
        this(plainBytesBuffer, key, iv, Utils::bindAnyAddress, Utils::bindAnyAddress);
    }

    private EncryptIVInDataWrapRingBuffer(ByteBufferRingBuffer plainBytesBuffer, BlockCipherKey key, byte[] iv,
                                          Supplier<InetSocketAddress> srcAddrSupplier,
                                          Supplier<InetSocketAddress> dstAddrSupplier) {
        super(plainBytesBuffer);
        //noinspection ReplaceNullCheck
        if (iv == null) {
            this.iv0 = CryptoUtils.randomBytes(key.ivLen());
        } else {
            this.iv0 = iv;
        }
        byte[] ivX = new byte[this.iv0.length];
        System.arraycopy(this.iv0, 0, ivX, 0, ivX.length);
        this.cipher = new StreamingCFBCipher(key, true, ivX);

        this.mirrorDataFactory = new MirrorDataFactory("iv-prepend", d -> {
            InetSocketAddress src = srcAddrSupplier.get();
            InetSocketAddress dst = dstAddrSupplier.get();
            d.setSrc(src).setDst(dst);
        });
    }

    private void mirror(ByteBuffer plain, int posBefore) {
        // build meta message
        String meta = "iv=" + ByteArray.from(iv0).toHexString() +
            ";";

        mirrorDataFactory.build()
            .setMeta(meta)
            .setDataAfter(plain, posBefore)
            .mirror();
    }

    @Override
    protected void handlePlainBuffer(ByteBuffer input, boolean[] errored, IOException[] ex) {
        final int plainInputPositionBefore = input.position();

        if (!ivSent) {
            recordIntermediateBuffer(ByteBuffer.wrap(iv0));
            ivSent = true;
        }
        int len = input.limit() - input.position();
        if (len == 0) {
            return;
        }
        byte[] buf = new byte[len];
        input.get(buf);

        byte[] res = cipher.update(buf, 0, buf.length);

        if (Mirror.isEnabled()) {
            mirror(input, plainInputPositionBefore);
        }

        recordIntermediateBuffer(ByteBuffer.wrap(res));
    }
}
