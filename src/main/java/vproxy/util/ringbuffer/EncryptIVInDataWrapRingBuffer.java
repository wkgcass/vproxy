package vproxy.util.ringbuffer;

import vproxy.util.RingBuffer;
import vproxy.util.crypto.BlockCipherKey;
import vproxy.util.crypto.StreamingCFBCipher;
import vproxy.util.crypto.Utils;

import java.nio.ByteBuffer;

public class EncryptIVInDataWrapRingBuffer extends AbstractWrapByteBufferRingBuffer implements RingBuffer {
    private boolean ivSent = false;
    private StreamingCFBCipher cipher;
    private final byte[] iv0;

    public EncryptIVInDataWrapRingBuffer(ByteBufferRingBuffer plainBytesBuffer, BlockCipherKey key) {
        this(plainBytesBuffer, key, null);
    }

    public EncryptIVInDataWrapRingBuffer(ByteBufferRingBuffer plainBytesBuffer, BlockCipherKey key, byte[] iv) {
        super(plainBytesBuffer);
        //noinspection ReplaceNullCheck
        if (iv == null) {
            this.iv0 = Utils.randomBytes(key.ivLen());
        } else {
            this.iv0 = iv;
        }
        byte[] ivX = new byte[this.iv0.length];
        System.arraycopy(this.iv0, 0, ivX, 0, ivX.length);
        this.cipher = new StreamingCFBCipher(key, true, ivX);
    }

    @Override
    protected void handlePlainBuffer(ByteBuffer input, boolean[] errored) {
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
        recordIntermediateBuffer(ByteBuffer.wrap(res));
    }
}
