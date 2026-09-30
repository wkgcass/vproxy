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
    }

    public static ByteArray sub(ByteArray source, int from, int len) {
        if (from < 0) {
            throw new IllegalArgumentException("from=" + from + " < 0");
        }
        if (len < 0) {
            throw new IllegalArgumentException("len=" + len + " < 0");
        }
        int sourceLen = source.length();
        if (from > sourceLen || len > sourceLen - from) {
            throw new ArrayIndexOutOfBoundsException("from=" + from + ", len=" + len + ", length=" + sourceLen);
        }
        if (from == 0 && len == sourceLen) {
            return source;
        }
        if (!(source instanceof SubByteArray)) {
            return new SubByteArray(source, from, len);
        }
        var src = (SubByteArray) source;
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
    public long retainedBackingSize() {
        return source.retainedBackingSize();
    }

    @Override
    public boolean retainedBackingSizeIsStable() {
        return source.retainedBackingSizeIsStable();
    }

    @Override
    boolean supportsDirectArrayCopy() {
        return source instanceof AbstractByteArray array && array.supportsDirectArrayCopy();
    }

    @Override
    boolean sharesBacking(SimpleByteArray target) {
        return source instanceof AbstractByteArray array && array.sharesBacking(target);
    }

    @Override
    protected void checkBoundForOffset(int off) {
        if (off < 0) {
            throw new IllegalArgumentException("off=" + off + " < 0");
        }
        if (off >= len) {
            throw new ArrayIndexOutOfBoundsException("off=" + off + ", length=" + len);
        }
    }

    @Override
    protected void checkBoundForOffsetAndLength(int off, int size) {
        if (size < 0) {
            throw new IllegalArgumentException("len=" + size + " < 0");
        }
        if (size > 0) {
            checkBoundForOffset(off);
        }
        if (off > len || (size > 0 && size > len - off)) {
            throw new ArrayIndexOutOfBoundsException("off=" + off + ", len=" + size + ", length=" + len);
        }
    }

    @Override
    public int uint16(int offset) {
        checkBoundForOffsetAndLength(offset, 2);
        return source.uint16(from + offset);
    }

    @Override
    public int uint16ReverseNetworkByteOrder(int offset) {
        checkBoundForOffsetAndLength(offset, 2);
        return source.uint16ReverseNetworkByteOrder(from + offset);
    }

    @Override
    public int int32(int offset) {
        checkBoundForOffsetAndLength(offset, 4);
        return source.int32(from + offset);
    }

    @Override
    public int int32ReverseNetworkByteOrder(int offset) {
        checkBoundForOffsetAndLength(offset, 4);
        return source.int32ReverseNetworkByteOrder(from + offset);
    }

    @Override
    public long uint32(int offset) {
        checkBoundForOffsetAndLength(offset, 4);
        return source.uint32(from + offset);
    }

    @Override
    public long uint32ReverseNetworkByteOrder(int offset) {
        checkBoundForOffsetAndLength(offset, 4);
        return source.uint32ReverseNetworkByteOrder(from + offset);
    }

    @Override
    public long int64(int offset) {
        checkBoundForOffsetAndLength(offset, 8);
        return source.int64(from + offset);
    }

    @Override
    public long int64ReverseNetworkByteOrder(int offset) {
        checkBoundForOffsetAndLength(offset, 8);
        return source.int64ReverseNetworkByteOrder(from + offset);
    }

    @Override
    public int uint24(int offset) {
        checkBoundForOffsetAndLength(offset, 3);
        return source.uint24(from + offset);
    }

    @Override
    public int uint24ReverseNetworkByteOrder(int offset) {
        checkBoundForOffsetAndLength(offset, 3);
        return source.uint24ReverseNetworkByteOrder(from + offset);
    }

    @Override
    public long updateChecksum(long state, int off, int size) {
        checkBoundForOffsetAndLength(off, size);
        return source.updateChecksum(state, from + off, size);
    }

    @Override
    public void copyInto(ByteArray dst, int dstOff, int srcOff, int srcLen) {
        if (dst instanceof SimpleByteArray target && supportsDirectArrayCopy() && !sharesBacking(target)
            && validRange(srcOff, srcLen, len) && validRange(dstOff, srcLen, target.length())) {
            source.copyInto(target, dstOff, from + srcOff, srcLen);
            return;
        }
        copyIntoSlow(dst, dstOff, srcOff, srcLen);
    }

    private boolean validRange(int off, int size, int total) {
        return off >= 0 && size >= 0 && off <= total && size <= total - off;
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
