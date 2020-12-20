package vproxybase.util.ringbuffer;

import vfd.IPPort;
import vfd.NetworkFD;
import vmirror.MirrorDataFactory;
import vproxybase.util.ByteArray;
import vproxybase.util.ByteBufferEx;
import vproxybase.util.Logger;
import vproxybase.util.RingBuffer;
import vproxybase.util.crypto.BlockCipherKey;
import vproxybase.util.crypto.CryptoUtils;
import vproxybase.util.crypto.StreamingCFBCipher;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

public class EncryptIVInDataWrapRingBuffer extends AbstractWrapByteBufferRingBuffer implements RingBuffer {
    private boolean ivSent = false;
    private final StreamingCFBCipher cipher;
    private final byte[] iv0;

    private final MirrorDataFactory mirrorDataFactory;

    public EncryptIVInDataWrapRingBuffer(ByteBufferRingBuffer plainBytesBuffer, BlockCipherKey key, NetworkFD<IPPort> fd) {
        this(plainBytesBuffer, key,
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

    private EncryptIVInDataWrapRingBuffer(ByteBufferRingBuffer plainBytesBuffer, BlockCipherKey key,
                                          Supplier<IPPort> localAddrSupplier,
                                          Supplier<IPPort> remoteAddrSupplier) {
        this(plainBytesBuffer, key, null, localAddrSupplier, remoteAddrSupplier);
        transferring = true; // we can transfer data at any time
    }

    public EncryptIVInDataWrapRingBuffer(ByteBufferRingBuffer plainBytesBuffer, BlockCipherKey key, byte[] iv) {
        this(plainBytesBuffer, key, iv, IPPort::bindAnyAddress, IPPort::bindAnyAddress);
    }

    private EncryptIVInDataWrapRingBuffer(ByteBufferRingBuffer plainBytesBuffer, BlockCipherKey key, byte[] iv,
                                          Supplier<IPPort> srcAddrSupplier,
                                          Supplier<IPPort> dstAddrSupplier) {
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
            IPPort src = srcAddrSupplier.get();
            IPPort dst = dstAddrSupplier.get();
            d.setSrc(src).setDst(dst);
        });
    }

    private void mirror(ByteBufferEx plain, int posBefore) {
        // build meta message
        String meta = "iv=" + ByteArray.from(iv0).toHexString() +
            ";";

        mirrorDataFactory.build()
            .setMeta(meta)
            .setDataAfter(plain, posBefore)
            .mirror();
    }

    @Override
    protected void handlePlainBuffer(ByteBufferEx input, boolean[] errored, IOException[] ex) {
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

        if (mirrorDataFactory.isEnabled()) {
            mirror(input, plainInputPositionBefore);
        }

        recordIntermediateBuffer(ByteBuffer.wrap(res));
    }
}
