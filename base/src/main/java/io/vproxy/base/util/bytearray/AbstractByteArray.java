package io.vproxy.base.util.bytearray;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Utils;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public abstract class AbstractByteArray implements ByteArray {
    boolean supportsDirectArrayCopy() {
        return false;
    }

    boolean sharesBacking(SimpleByteArray target) {
        return false;
    }

    void copyIntoSlow(ByteArray dst, int dstOff, int srcOff, int srcLen) {
        for (int i = 0; i < srcLen; ++i) {
            dst.set(dstOff + i, get(srcOff + i));
        }
    }

    public static final ByteArray EMPTY = new SimpleByteArray(Utils.getZeroLengthByteArray());

    protected void checkBoundForOffset(int off) {
        if (off < 0) {
            throw new IllegalArgumentException("off=" + off + " < 0");
        }
        if (off >= length()) {
            throw new ArrayIndexOutOfBoundsException("off=" + off + ", length=" + length());
        }
    }

    protected void checkBoundForOffsetAndLength(int off, int len) {
        if (len < 0) {
            throw new IllegalArgumentException("len=" + len + " < 0");
        }
        if (len > 0) {
            checkBoundForOffset(off);
        }
        int size = length();
        if (off > size || (len > 0 && len > size - off)) {
            throw new ArrayIndexOutOfBoundsException("off=" + off + ", len=" + len + ", length=" + size);
        }
    }

    void writeMultiByteSlow(int offset, long value, int bytes, boolean reverse) {
        for (int i = 0; i < bytes; ++i) {
            int shift = (reverse ? i : bytes - i - 1) * 8;
            set(offset + i, (byte) (value >>> shift));
        }
    }

    protected void checkBoundForByteBufferAndOffsetAndLength(ByteBuffer byteBuffer, int off, int len) {
        checkBoundForOffsetAndLength(off, len);

        int bLen = byteBuffer.limit() - byteBuffer.position();
        if (bLen < len) {
            throw new IndexOutOfBoundsException("byteBuffer.length=" + bLen + ", len=" + len);
        }
    }

    @Override
    public void toNewJavaArray(byte[] holder, int off) {
        int len = length();
        if (len > holder.length - off) {
            throw new IllegalArgumentException("input byte[] and offset cannot hold bytes in this ByteArray: input=" + holder.length + ", off=" + off + ", this=" + len);
        }
        doToNewJavaArray(holder, off, 0, len);
    }

    abstract protected void doToNewJavaArray(byte[] dst, int dstOff, int srcOff, int srcLen);

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ByteArray))
            return false;
        ByteArray o = (ByteArray) obj;

        final int len = this.length();

        if (len != o.length())
            return false;

        for (int i = 0; i < len; ++i) {
            if (this.get(i) != o.get(i))
                return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        final int len = length();
        int ret = 0x811c9dc5;
        for (int i = 0; i < len; ++i) {
            ret = (ret ^ (get(i) & 0xff)) * 0x01000193;
        }
        return ret;
    }

    @Override
    public String toString() {
        return toString(StandardCharsets.UTF_8);
    }

    public String toString(Charset charset) {
        return new String(toJavaArray(), charset);
    }
}
