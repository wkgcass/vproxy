package io.vproxy.base.util.ringbuffer;

import io.vproxy.base.util.*;
import io.vproxy.vmirror.MirrorDataFactory;
import vproxy.base.util.*;
import io.vproxy.base.util.crypto.BlockCipherKey;
import io.vproxy.base.util.crypto.StreamingCFBCipher;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.NetworkFD;
import io.vproxy.vmirror.MirrorDataFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

public class DecryptIVInDataUnwrapRingBuffer extends AbstractUnwrapByteBufferRingBuffer implements RingBuffer {
    private final BlockCipherKey key;

    private int requiredIvLen;
    private final byte[] iv;
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
        this.iv = Utils.allocateByteArray(requiredIvLen);

        this.mirrorDataFactory = new MirrorDataFactory("iv-prepend", d -> {
            IPPort src = srcAddrSupplier.get();
            IPPort dst = dstAddrSupplier.get();
            d.setSrc(src).setDst(dst);
        });
    }

    @Override
    protected void handleEncryptedBuffer(ByteBufferEx buf, boolean[] underflow, boolean[] errored, IOException[] ex) {
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

    private void readIv(ByteBufferEx buf) {
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

    private void readData(ByteBufferEx input) {
        int len = input.limit() - input.position();
        if (len == 0) {
            return;
        }
        byte[] array = Utils.allocateByteArray(len);
        input.get(array);

        byte[] ret = cipher.update(array, 0, array.length);

        if (mirrorDataFactory.isEnabled()) {
            mirror(ret);
        }

        assert Logger.lowLevelDebug("decrypt " + ret.length + " bytes");
        assert Logger.lowLevelNetDebugPrintBytes(ret);
        recordIntermediateBuffer(ByteBuffer.wrap(ret));
    }
}
