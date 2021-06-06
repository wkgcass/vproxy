package vproxy.base.util.bytearray;

import vproxy.base.util.ByteArray;

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
        checkBoundForOffset(idx);

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
        checkBoundForOffset(idx);

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
        checkBoundForByteBufferAndOffsetAndLength(dst, off, len);

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
        checkBoundForByteBufferAndOffsetAndLength(src, off, len);

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

    @Override
    protected void doToNewJavaArray(byte[] dst, int dstOff, int srcOff, int srcLen) {
        int firstLen = first.length();
        if (firstLen > srcOff) {
            //noinspection UnnecessaryLocalVariable
            int off = srcOff;
            int len = Math.min(srcLen, firstLen - off);
            ((AbstractByteArray) first).doToNewJavaArray(dst, dstOff, off, len);
            if (srcLen > len) {
                ((AbstractByteArray) second).doToNewJavaArray(dst, dstOff + len, 0, srcLen - len);
            }
        } else {
            ((AbstractByteArray) second).doToNewJavaArray(dst, dstOff, srcOff - firstLen, srcLen);
        }
    }
}
