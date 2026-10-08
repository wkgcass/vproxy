package io.vproxy.base.util.bytearray;

import io.vproxy.base.util.ByteArray;

import java.nio.ByteBuffer;

public class SimpleByteArray extends AbstractByteArray implements ByteArray {
    private final byte[] array;

    @Override
    boolean supportsDirectArrayCopy() {
        return true;
    }

    @Override
    boolean sharesBacking(SimpleByteArray target) {
        return array == target.array;
    }

    void copyFrom(AbstractByteArray source, int dstOff, int srcOff, int srcLen) {
        source.doToNewJavaArray(array, dstOff, srcOff, srcLen);
    }

    public SimpleByteArray(byte[] array) {
        this.array = array;
    }

    @Override
    protected void checkBoundForOffset(int off) {
        if (off < 0) {
            throw new IllegalArgumentException("off=" + off + " < 0");
        }
        if (off >= array.length) {
            throw new ArrayIndexOutOfBoundsException("off=" + off + ", length=" + array.length);
        }
    }

    @Override
    protected void checkBoundForOffsetAndLength(int off, int len) {
        if (len < 0) {
            throw new IllegalArgumentException("len=" + len + " < 0");
        }
        if (len > 0) {
            checkBoundForOffset(off);
        }
        if (off > array.length || (len > 0 && len > array.length - off)) {
            throw new ArrayIndexOutOfBoundsException("off=" + off + ", len=" + len + ", length=" + array.length);
        }
    }

    @Override
    public byte get(int idx) {
        checkBoundForOffset(idx);
        return array[idx];
    }

    @Override
    public ByteArray set(int idx, byte value) {
        checkBoundForOffset(idx);
        array[idx] = value;
        return this;
    }

    @Override
    public int length() {
        return array.length;
    }

    @Override
    public int uint16(int offset) {
        checkBoundForOffsetAndLength(offset, 2);
        return ((array[offset] & 0xff) << 8) | (array[offset + 1] & 0xff);
    }

    @Override
    public int uint16ReverseNetworkByteOrder(int offset) {
        checkBoundForOffsetAndLength(offset, 2);
        return ((array[offset + 1] & 0xff) << 8) | (array[offset] & 0xff);
    }

    @Override
    public int int32(int offset) {
        checkBoundForOffsetAndLength(offset, 4);
        return (array[offset] & 0xff) << 24 | (array[offset + 1] & 0xff) << 16 |
            (array[offset + 2] & 0xff) << 8 | (array[offset + 3] & 0xff);
    }

    @Override
    public int int32ReverseNetworkByteOrder(int offset) {
        checkBoundForOffsetAndLength(offset, 4);
        return (array[offset + 3] & 0xff) << 24 | (array[offset + 2] & 0xff) << 16 |
            (array[offset + 1] & 0xff) << 8 | (array[offset] & 0xff);
    }

    @Override
    public int uint24(int offset) {
        checkBoundForOffsetAndLength(offset, 3);
        return (array[offset] & 0xff) << 16 | (array[offset + 1] & 0xff) << 8 | (array[offset + 2] & 0xff);
    }

    @Override
    public int uint24ReverseNetworkByteOrder(int offset) {
        checkBoundForOffsetAndLength(offset, 3);
        return (array[offset + 2] & 0xff) << 16 | (array[offset + 1] & 0xff) << 8 | (array[offset] & 0xff);
    }

    @Override
    public long uint32(int offset) {
        return int32(offset) & 0xffffffffL;
    }

    @Override
    public long uint32ReverseNetworkByteOrder(int offset) {
        return int32ReverseNetworkByteOrder(offset) & 0xffffffffL;
    }

    @Override
    public long int64(int offset) {
        checkBoundForOffsetAndLength(offset, 8);
        return ((long) (array[offset] & 0xff) << 56) | ((long) (array[offset + 1] & 0xff) << 48) |
            ((long) (array[offset + 2] & 0xff) << 40) | ((long) (array[offset + 3] & 0xff) << 32) |
            ((long) (array[offset + 4] & 0xff) << 24) | ((long) (array[offset + 5] & 0xff) << 16) |
            ((long) (array[offset + 6] & 0xff) << 8) | (array[offset + 7] & 0xffL);
    }

    @Override
    public long int64ReverseNetworkByteOrder(int offset) {
        checkBoundForOffsetAndLength(offset, 8);
        return ((long) (array[offset + 7] & 0xff) << 56) | ((long) (array[offset + 6] & 0xff) << 48) |
            ((long) (array[offset + 5] & 0xff) << 40) | ((long) (array[offset + 4] & 0xff) << 32) |
            ((long) (array[offset + 3] & 0xff) << 24) | ((long) (array[offset + 2] & 0xff) << 16) |
            ((long) (array[offset + 1] & 0xff) << 8) | (array[offset] & 0xffL);
    }

    @Override
    public ByteArray int16(int offset, int val) {
        if (!validRange(offset, 2, array.length)) {
            writeMultiByteSlow(offset, val, 2, false);
            return this;
        }
        checkBoundForOffsetAndLength(offset, 2);
        array[offset] = (byte) (val >>> 8);
        array[offset + 1] = (byte) val;
        return this;
    }

    @Override
    public ByteArray int16ReverseNetworkByteOrder(int offset, int val) {
        if (!validRange(offset, 2, array.length)) {
            writeMultiByteSlow(offset, val, 2, true);
            return this;
        }
        checkBoundForOffsetAndLength(offset, 2);
        array[offset] = (byte) val;
        array[offset + 1] = (byte) (val >>> 8);
        return this;
    }

    @Override
    public ByteArray int32(int offset, int val) {
        if (!validRange(offset, 4, array.length)) {
            writeMultiByteSlow(offset, val, 4, false);
            return this;
        }
        checkBoundForOffsetAndLength(offset, 4);
        array[offset] = (byte) (val >>> 24);
        array[offset + 1] = (byte) (val >>> 16);
        array[offset + 2] = (byte) (val >>> 8);
        array[offset + 3] = (byte) val;
        return this;
    }

    @Override
    public ByteArray int32ReverseNetworkByteOrder(int offset, int val) {
        if (!validRange(offset, 4, array.length)) {
            writeMultiByteSlow(offset, val, 4, true);
            return this;
        }
        checkBoundForOffsetAndLength(offset, 4);
        array[offset] = (byte) val;
        array[offset + 1] = (byte) (val >>> 8);
        array[offset + 2] = (byte) (val >>> 16);
        array[offset + 3] = (byte) (val >>> 24);
        return this;
    }

    @Override
    public ByteArray int64(int offset, long val) {
        if (!validRange(offset, 8, array.length)) {
            writeMultiByteSlow(offset, val, 8, false);
            return this;
        }
        checkBoundForOffsetAndLength(offset, 8);
        for (int i = 7; i >= 0; --i) {
            array[offset + 7 - i] = (byte) (val >>> (i * 8));
        }
        return this;
    }

    @Override
    public ByteArray int64ReverseNetworkByteOrder(int offset, long val) {
        if (!validRange(offset, 8, array.length)) {
            writeMultiByteSlow(offset, val, 8, true);
            return this;
        }
        checkBoundForOffsetAndLength(offset, 8);
        for (int i = 0; i < 8; ++i) {
            array[offset + i] = (byte) (val >>> (i * 8));
        }
        return this;
    }

    @Override
    public long updateChecksum(long state, int off, int len) {
        checkBoundForOffsetAndLength(off, len);
        long sum = state >> 9;
        boolean pending = ((state >>> 8) & 1) != 0;
        int high = (int) (state & 0xff);
        int i = off;
        int end = off + len;
        if (pending && i < end) {
            sum += high << 8 | array[i++] & 0xff;
            pending = false;
        }
        int words = 0;
        while (i + 1 < end) {
            sum += (array[i] & 0xff) << 8 | array[i + 1] & 0xff;
            i += 2;
            if (++words == 16384) {
                sum = (sum & 0xffff) + (sum >>> 16);
                words = 0;
            }
        }
        if (i < end) {
            high = array[i] & 0xff;
            pending = true;
        }
        // Keep the state bounded across leaves, including leaves smaller than
        // one folding batch. Otherwise many small leaves can overflow a later
        // int accumulator even though each individual leaf is short.
        sum = (sum & 0xffff) + (sum >>> 16);
        sum = (sum & 0xffff) + (sum >>> 16);
        return (sum << 9) | (pending ? 1L << 8 : 0) | high;
    }

    @Override
    public void copyInto(ByteArray dst, int dstOff, int srcOff, int srcLen) {
        if (!(dst instanceof SimpleByteArray target) || target.array == array) {
            copyIntoSlow(dst, dstOff, srcOff, srcLen);
            return;
        }
        if (!validRange(srcOff, srcLen, array.length) || !validRange(dstOff, srcLen, target.array.length)) {
            copyIntoSlow(dst, dstOff, srcOff, srcLen);
            return;
        }
        System.arraycopy(array, srcOff, target.array, dstOff, srcLen);
    }

    private boolean validRange(int off, int len, int size) {
        return off >= 0 && len >= 0 && off <= size && len <= size - off;
    }

    @Override
    public byte[] toJavaArray() {
        return array;
    }

    @Override
    public ByteArray arrange() {
        return this;
    }

    @Override
    public void byteBufferPut(ByteBuffer dst, int off, int len) {
        checkBoundForByteBufferAndOffsetAndLength(dst, off, len);
        dst.put(array, off, len);
    }

    @Override
    public void byteBufferGet(ByteBuffer src, int off, int len) {
        checkBoundForByteBufferAndOffsetAndLength(src, off, len);
        src.get(array, off, len);
    }

    @Override
    protected void doToNewJavaArray(byte[] dst, int dstOff, int srcOff, int srcLen) {
        System.arraycopy(array, srcOff, dst, dstOff, srcLen);
    }
}
