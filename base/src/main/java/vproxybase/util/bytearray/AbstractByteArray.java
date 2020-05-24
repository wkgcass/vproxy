package vproxybase.util.bytearray;

import vproxybase.util.ByteArray;

import java.nio.ByteBuffer;

public abstract class AbstractByteArray implements ByteArray {
    public static final ByteArray EMPTY = new SimpleByteArray(new byte[0]);

    protected void checkBoundForOffset(int off) {
        if (off < 0) {
            throw new IllegalArgumentException("off=" + off + " < 0");
        }
        if (off >= length()) {
            throw new ArrayIndexOutOfBoundsException("off=" + off + ", length=" + length());
        }
    }

    protected void checkBoundForOffsetAndLength(int off, int len) {
        if (len > 0) {
            checkBoundForOffset(off);
        }
        if (len < 0) {
            throw new IllegalArgumentException("len=" + len + " < 0");
        }
        if (off > length() || off + len > length()) {
            throw new ArrayIndexOutOfBoundsException("off=" + off + ", len=" + len + ", length=" + length());
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
        int ret = 0;
        for (int i = 0; i < len; ++i) {
            ret = (ret << 31) | get(i);
        }
        return ret;
    }

    @Override
    public String toString() {
        return toHexString();
    }
}
