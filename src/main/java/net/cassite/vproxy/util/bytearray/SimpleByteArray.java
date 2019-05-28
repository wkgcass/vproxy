package net.cassite.vproxy.util.bytearray;

import net.cassite.vproxy.util.ByteArray;

import java.nio.ByteBuffer;

public class SimpleByteArray extends AbstractByteArray implements ByteArray {
    private final byte[] array;

    public SimpleByteArray(byte[] array) {
        this.array = array;
    }

    @Override
    public byte get(int idx) {
        return array[idx];
    }

    @Override
    public ByteArray set(int idx, byte value) {
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
        dst.put(array, off, len);
    }

    @Override
    public void byteBufferGet(ByteBuffer src, int off, int len) {
        src.get(array, off, len);
    }
}
