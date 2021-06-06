package vproxy.base.util.bytearray;

import vproxy.base.util.ByteArray;

import java.nio.ByteBuffer;

public class OneByteArray extends AbstractByteArray {
    private byte b;

    public OneByteArray() {
    }

    public OneByteArray(byte b) {
        this.b = b;
    }

    @Override
    public byte get(int idx) {
        if (idx == 0)
            return b;
        throw new ArrayIndexOutOfBoundsException("off=" + idx);
    }

    @Override
    public ByteArray set(int idx, byte value) {
        if (idx == 0) {
            b = value;
            return this;
        }
        throw new ArrayIndexOutOfBoundsException("off=" + idx);
    }

    @Override
    public int length() {
        return 1;
    }

    @Override
    public void byteBufferPut(ByteBuffer dst, int off, int len) {
        checkBoundForByteBufferAndOffsetAndLength(dst, off, len);
        if (len == 0)
            return;
        dst.put(b);
    }

    @Override
    public void byteBufferGet(ByteBuffer src, int off, int len) {
        checkBoundForByteBufferAndOffsetAndLength(src, off, len);
        if (len == 0)
            return;
        b = src.get();
    }

    @Override
    protected void doToNewJavaArray(byte[] dst, int dstOff, int srcOff, int srcLen) {
        dst[dstOff] = b;
    }
}
