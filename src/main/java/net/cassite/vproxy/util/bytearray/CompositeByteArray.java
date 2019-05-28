package net.cassite.vproxy.util.bytearray;

import net.cassite.vproxy.util.ByteArray;

import java.nio.ByteBuffer;

public class CompositeByteArray extends AbstractByteArray implements ByteArray {
    private final ByteArray first;
    private final ByteArray second;
    private final int len;

    public CompositeByteArray(ByteArray first, ByteArray second) {
        this.first = first;
        this.second = second;

        this.len = first.length() + second.length();
    }

    @Override
    public byte get(int idx) {
        if (idx >= len || idx < 0)
            throw new ArrayIndexOutOfBoundsException("index=" + idx + ", length=" + len);
        if (idx < first.length()) {
            return first.get(idx);
        } else {
            return second.get(idx - first.length());
        }
    }

    @Override
    public ByteArray set(int idx, byte value) {
        if (idx >= len || idx < 0)
            throw new ArrayIndexOutOfBoundsException("index=" + idx + ", length=" + len);
        if (idx < first.length()) {
            first.set(idx, value);
        } else {
            second.set(idx - first.length(), value);
        }
        return this;
    }

    @Override
    public int length() {
        return len;
    }

    @Override
    public void byteBufferPut(ByteBuffer dst, int off, int len) {
        int firstLen = first.length();
        if (off > firstLen) {
            second.byteBufferPut(dst, off - firstLen, len);
            return;
        }
        if (len < firstLen - off) {
            first.byteBufferPut(dst, off, len);
            return;
        }
        first.byteBufferPut(dst, off, firstLen - off);
        second.byteBufferPut(dst, 0, len - (firstLen - off));
    }

    @Override
    public void byteBufferGet(ByteBuffer src, int off, int len) {
        int firstLen = first.length();
        if (off > firstLen) {
            second.byteBufferGet(src, off - firstLen, len);
            return;
        }
        if (len < firstLen - off) {
            first.byteBufferGet(src, off, len);
            return;
        }
        first.byteBufferGet(src, off, firstLen - off);
        second.byteBufferGet(src, 0, len - (firstLen - off));
    }
}
