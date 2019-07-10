package vproxy.util.bytearray;

import vproxy.util.ByteArray;

import java.nio.ByteBuffer;

public class OneByteArray extends AbstractByteArray {
    private byte b;

    public OneByteArray() {
    }

    public OneByteArray(byte b) {
        this.b = b;
    }

    @Override
    public byte get(int idx) {
        if (idx == 0)
            return b;
        throw new ArrayIndexOutOfBoundsException("" + idx);
    }

    @Override
    public ByteArray set(int idx, byte value) {
        if (idx == 0) {
            b = value;
            return this;
        }
        throw new ArrayIndexOutOfBoundsException("" + idx);
    }

    @Override
    public int length() {
        return 1;
    }

    @Override
    public void byteBufferPut(ByteBuffer dst, int off, int len) {
        if (len > 1) {
            throw new ArrayIndexOutOfBoundsException("" + len);
        }
        if (len == 0)
            return;
        dst.put(off, b);
    }

    @Override
    public void byteBufferGet(ByteBuffer src, int off, int len) {
        if (len > 1) {
            throw new ArrayIndexOutOfBoundsException("" + len);
        }
        if (len == 0)
            return;
        b = src.get(off);
    }
}
