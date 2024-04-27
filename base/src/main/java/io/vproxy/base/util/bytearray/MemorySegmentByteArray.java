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
