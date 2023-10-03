package io.vproxy.base.util.bytearray;

import io.vproxy.base.util.ByteArray;

import java.nio.ByteBuffer;

public class SubByteArray extends AbstractByteArray implements ByteArray {
    public final ByteArray source;
    public final int from;
    public final int len;

    private SubByteArray(ByteArray source, int from, int len) {
        this.source = source;
        this.from = from;
        this.len = len;

        if (source.length() - from < len)
            throw new ArrayIndexOutOfBoundsException("from=" + from + ", len=" + len + ", length=" + source.length());
    }

    public static ByteArray sub(ByteArray source, int from, int len) {
        if (from == 0 && len == source.length()) {
            return source;
        }
        if (!(source instanceof SubByteArray)) {
            return new SubByteArray(source, from, len);
        }
        var src = (SubByteArray) source;
        if (src.len < from + len) {
            throw new ArrayIndexOutOfBoundsException("src.len=" + src.len + ", from=" + from + ", len=" + len);
        }
        return new SubByteArray(src.source, src.from + from, len);
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
