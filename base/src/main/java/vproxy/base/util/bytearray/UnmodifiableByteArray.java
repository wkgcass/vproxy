package vproxy.base.util.bytearray;

import vproxy.base.util.ByteArray;

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
