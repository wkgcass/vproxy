package net.cassite.vproxy.util.bytearray;

import net.cassite.vproxy.util.ByteArray;

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
        if (idx >= len || idx < 0)
            throw new ArrayIndexOutOfBoundsException("index=" + idx + ", length=" + len);
        return source.get(idx + from);
    }

    @Override
    public ByteArray set(int idx, byte value) {
        if (idx >= len || idx < 0)
            throw new ArrayIndexOutOfBoundsException("index=" + idx + ", length=" + len);
        source.set(from + idx, value);
        return this;
    }

    @Override
    public int length() {
        return len;
    }

    @Override
    public void byteBufferPut(ByteBuffer dst, int off, int len) {
        source.byteBufferPut(dst, off + from, len);
    }

    @Override
    public void byteBufferGet(ByteBuffer src, int off, int len) {
        source.byteBufferGet(src, off + from, len);
    }
}
