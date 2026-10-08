package io.vproxy.base.util.bytearray;

import io.vproxy.base.util.ByteArray;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MemorySegmentByteArray extends AbstractByteArray implements ByteArray {
    protected final MemorySegment seg;

    public MemorySegmentByteArray(MemorySegment seg) {
        this.seg = seg;
    }

    public MemorySegment getMemorySegment() {
        return seg;
    }

    @Override
    public byte get(int idx) {
        return seg.get(ValueLayout.JAVA_BYTE, idx);
    }

    @Override
    public ByteArray set(int idx, byte value) {
        seg.set(ValueLayout.JAVA_BYTE, idx, value);
        return this;
    }

    @Override
    public int length() {
        return (int) seg.byteSize();
    }

    @Override
    public void byteBufferPut(ByteBuffer dst, int off, int len) {
        dst.put(seg.asByteBuffer().limit(off + len).position(off));
    }

    @Override
    public void byteBufferGet(ByteBuffer src, int off, int len) {
        seg.asByteBuffer().limit(off + len).position(off).put(src);
    }

    @Override
    public void copyInto(ByteArray dst, int dstOff, int srcOff, int srcLen) {
        if (!(dst instanceof MemorySegmentByteArray segBuf)) {
            super.copyInto(dst, dstOff, srcOff, srcLen);
            return;
        }
        segBuf.seg.asSlice(dstOff, srcLen).copyFrom(this.seg.asSlice(srcOff, srcLen));
    }

    @Override
    protected void doToNewJavaArray(byte[] dst, int dstOff, int srcOff, int srcLen) {
        seg.asByteBuffer().limit(srcOff + srcLen).position(srcOff).get(dst, dstOff, srcLen);
    }

    private static final ValueLayout.OfLong LONG_BIG_ENDIAN = ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfLong LONG_LITTLE_ENDIAN = ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);

    @Override
    public long int64(int offset) {
        return seg.get(LONG_BIG_ENDIAN, offset);
    }

    @Override
    public long int64ReverseNetworkByteOrder(int offset) {
        return seg.get(LONG_LITTLE_ENDIAN, offset);
    }

    private static final ValueLayout.OfInt INT_BIG_ENDIAN = ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfInt INT_LITTLE_ENDIAN = ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);

    @Override
    public int int32(int offset) {
        return seg.get(INT_BIG_ENDIAN, offset);
    }

    @Override
    public int int32ReverseNetworkByteOrder(int offset) {
        return seg.get(INT_LITTLE_ENDIAN, offset);
    }

    @Override
    public long uint32(int offset) {
        return int32(offset) & 0xffffffffL;
    }

    @Override
    public long uint32ReverseNetworkByteOrder(int offset) {
        return int32ReverseNetworkByteOrder(offset) & 0xffffffffL;
    }

    private static final ValueLayout.OfShort SHORT_BIG_ENDIAN = ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfShort SHORT_LITTLE_ENDIAN = ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);

    @Override
    public int uint16(int offset) {
        return seg.get(SHORT_BIG_ENDIAN, offset) & 0xffff;
    }

    @Override
    public int uint16ReverseNetworkByteOrder(int offset) {
        return seg.get(SHORT_LITTLE_ENDIAN, offset) & 0xffff;
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
            sum += high << 8 | seg.get(ValueLayout.JAVA_BYTE, i++) & 0xff;
            pending = false;
        }
        int words = 0;
        // One checked, unaligned FFM access supplies four network-order words.
        // Keep the short tail and odd-byte streaming state identical to the
        // scalar path; no temporary array or unchecked native access is used.
        while (end - i >= Long.BYTES) {
            long packed = seg.get(LONG_BIG_ENDIAN, i);
            sum += (packed >>> 48) + ((packed >>> 32) & 0xffff)
                + ((packed >>> 16) & 0xffff) + (packed & 0xffff);
            i += Long.BYTES;
            words += 4;
            if (words == 16384) {
                sum = (sum & 0xffff) + (sum >>> 16);
                words = 0;
            }
        }
        while (i + 1 < end) {
            sum += seg.get(SHORT_BIG_ENDIAN, i) & 0xffff;
            i += 2;
            if (++words == 16384) {
                sum = (sum & 0xffff) + (sum >>> 16);
                words = 0;
            }
        }
        if (i < end) {
            high = seg.get(ValueLayout.JAVA_BYTE, i) & 0xff;
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
    public ByteArray int16(int offset, int val) {
        seg.set(SHORT_BIG_ENDIAN, offset, (short) val);
        return this;
    }

    @Override
    public ByteArray int16ReverseNetworkByteOrder(int offset, int val) {
        seg.set(SHORT_LITTLE_ENDIAN, offset, (short) val);
        return this;
    }

    @Override
    public ByteArray int32(int offset, int val) {
        seg.set(INT_BIG_ENDIAN, offset, val);
        return this;
    }

    @Override
    public ByteArray int32ReverseNetworkByteOrder(int offset, int val) {
        seg.set(INT_LITTLE_ENDIAN, offset, val);
        return this;
    }

    @Override
    public ByteArray int64(int offset, long val) {
        seg.set(LONG_BIG_ENDIAN, offset, val);
        return this;
    }

    @Override
    public ByteArray int64ReverseNetworkByteOrder(int offset, long val) {
        seg.set(LONG_LITTLE_ENDIAN, offset, val);
        return this;
    }

    @Override
    public ByteArray sub(int fromInclusive, int len) {
        var newSeg = seg.asSlice(fromInclusive, len);
        return new MemorySegmentByteArray(newSeg);
    }
}
