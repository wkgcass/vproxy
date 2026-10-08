package io.vproxy.base.util.bytearray;

import io.vproxy.base.util.ByteArray;

import java.nio.ByteBuffer;

public class UnmodifiableByteArray extends AbstractByteArray implements ByteArray {
    private final ByteArray array;

    public UnmodifiableByteArray(ByteArray array) {
        this.array = array;
    }

    @Override
    public ByteArray unmodifiable() {
        return this;
    }

    @Override
    public byte[] toJavaArray() {
        return array.toJavaArray();
    }

    @Override
    public byte get(int idx) {
        return array.get(idx);
    }

    @Override
    public ByteArray set(int idx, byte value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int length() {
        return array.length();
    }

    @Override
    public long retainedBackingSize() {
        // concatOrCopy historically treats this wrapper as an opaque leaf.
        return length();
    }

    @Override
    public boolean retainedBackingSizeIsStable() {
        return array.retainedBackingSizeIsStable();
    }

    @Override
    boolean supportsDirectArrayCopy() {
        return array instanceof AbstractByteArray abstractArray && abstractArray.supportsDirectArrayCopy();
    }

    @Override
    boolean sharesBacking(SimpleByteArray target) {
        return array instanceof AbstractByteArray abstractArray && abstractArray.sharesBacking(target);
    }

    @Override
    public int uint16(int offset) {
        return array.uint16(offset);
    }

    @Override
    public int uint16ReverseNetworkByteOrder(int offset) {
        return array.uint16ReverseNetworkByteOrder(offset);
    }

    @Override
    public int int32(int offset) {
        return array.int32(offset);
    }

    @Override
    public int int32ReverseNetworkByteOrder(int offset) {
        return array.int32ReverseNetworkByteOrder(offset);
    }

    @Override
    public long uint32(int offset) {
        return array.uint32(offset);
    }

    @Override
    public long uint32ReverseNetworkByteOrder(int offset) {
        return array.uint32ReverseNetworkByteOrder(offset);
    }

    @Override
    public long int64(int offset) {
        return array.int64(offset);
    }

    @Override
    public long int64ReverseNetworkByteOrder(int offset) {
        return array.int64ReverseNetworkByteOrder(offset);
    }

    @Override
    public int uint24(int offset) {
        return array.uint24(offset);
    }

    @Override
    public int uint24ReverseNetworkByteOrder(int offset) {
        return array.uint24ReverseNetworkByteOrder(offset);
    }

    @Override
    public long updateChecksum(long state, int off, int len) {
        if (array instanceof AbstractByteArray abstractArray) {
            checkBoundForOffsetAndLength(off, len);
            return abstractArray.updateChecksum(state, off, len);
        }
        return array.updateChecksum(state, off, len);
    }

    @Override
    public void copyInto(ByteArray dst, int dstOff, int srcOff, int srcLen) {
        if (dst instanceof SimpleByteArray target && supportsDirectArrayCopy() && !sharesBacking(target)
            && validRange(srcOff, srcLen, length()) && validRange(dstOff, srcLen, target.length())) {
            array.copyInto(target, dstOff, srcOff, srcLen);
            return;
        }
        copyIntoSlow(dst, dstOff, srcOff, srcLen);
    }

    private boolean validRange(int off, int len, int size) {
        return off >= 0 && len >= 0 && off <= size && len <= size - off;
    }

    @Override
    public void byteBufferPut(ByteBuffer dst, int off, int len) {
        array.byteBufferPut(dst, off, len);
    }

    @Override
    public void byteBufferGet(ByteBuffer src, int off, int len) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doToNewJavaArray(byte[] dst, int dstOff, int srcOff, int srcLen) {
        if (array instanceof AbstractByteArray) {
            ((AbstractByteArray) array).doToNewJavaArray(dst, dstOff, srcOff, srcLen);
        } else {
            for (int i = 0; i < srcLen; ++i) {
                dst[dstOff + i] = array.get(srcOff + i);
            }
        }
    }
}
