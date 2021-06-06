package vproxy.base.util.bytearray;

import vproxy.base.util.ByteArray;

import java.nio.ByteBuffer;

public class SubByteArray extends AbstractByteArray implements ByteArray {
    private final ByteArray source;
    private final int from;
    private final int len;

    public SubByteArray(ByteArray source, int from, int len) {
        this.source = source;
        this.from = from;
        this.len = len;

        if (source.length() - from < len)
            throw new ArrayIndexOutOfBoundsException("from=" + from + ", len=" + len + ", length=" + source.length());
    }

    @Override
    public byte get(int idx) {
        checkBoundForOffset(idx);
        return source.get(idx + from);
    }

    @Override
    public ByteArray set(int idx, byte value) {
        checkBoundForOffset(idx);
        source.set(from + idx, value);
        return this;
    }

    @Override
    public int length() {
        return len;
    }

    @Override
    public void byteBufferPut(ByteBuffer dst, int off, int len) {
        checkBoundForByteBufferAndOffsetAndLength(dst, off, len);
        source.byteBufferPut(dst, off + from, len);
    }

    @Override
    public void byteBufferGet(ByteBuffer src, int off, int len) {
        checkBoundForByteBufferAndOffsetAndLength(src, off, len);
        source.byteBufferGet(src, off + from, len);
    }

    @Override
    protected void doToNewJavaArray(byte[] dst, int dstOff, int srcOff, int srcLen) {
        ((AbstractByteArray) source).doToNewJavaArray(dst, dstOff, from + srcOff, srcLen);
    }
}
