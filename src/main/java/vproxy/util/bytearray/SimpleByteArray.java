package vproxy.util.bytearray;

import vproxy.util.ByteArray;

import java.nio.ByteBuffer;

public class SimpleByteArray extends AbstractByteArray implements ByteArray {
    private final byte[] array;

    public SimpleByteArray(byte[] array) {
        this.array = array;
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
