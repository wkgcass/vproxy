package vproxy.util;

import vproxy.util.bytearray.CompositeByteArray;
import vproxy.util.bytearray.OneByteArray;
import vproxy.util.bytearray.SimpleByteArray;
import vproxy.util.bytearray.SubByteArray;
import vproxy.util.nio.ByteArrayChannel;

import java.nio.ByteBuffer;

@SuppressWarnings("UnusedReturnValue")
public interface ByteArray {
    static ByteArray allocate(int len) {
        if (len == 0) {
            throw new IllegalArgumentException();
        } else if (len == 1) {
            return new OneByteArray();
        } else {
            return from(new byte[len]);
        }
    }

    static ByteArray from(byte b) {
        return new OneByteArray(b);
    }

    static ByteArray from(byte[] array) {
        return new SimpleByteArray(array);
    }

    static ByteArray from(int... array) {
        byte[] bytes = new byte[array.length];
        for (int i = 0; i < array.length; ++i) {
            bytes[i] = (byte) array[i];
        }
        return from(bytes);
    }

    byte get(int idx);

    ByteArray set(int idx, byte value);

    int length();

    default ByteArray sub(int fromInclusive, int len) {
        return new SubByteArray(this, fromInclusive, len);
    }

    default ByteArray concat(ByteArray array) {
        return new CompositeByteArray(this, array);
    }

    default byte[] toJavaArray() {
        return toNewJavaArray();
    }

    default byte[] toNewJavaArray() {
        int len = length();
        byte[] array = new byte[len];
        for (int i = 0; i < len; ++i) {
            array[i] = get(i);
        }
        return array;
    }

    default ByteArray arrange() {
        return new SimpleByteArray(toNewJavaArray());
    }

    default ByteArray copy() {
        return ByteArray.from(toNewJavaArray());
    }

    default ByteArrayChannel toFullChannel() {
        return ByteArrayChannel.fromFull(this);
    }

    default int uint24(int offset) {
        return uint8(offset) << 16 | uint8(offset + 1) << 8 | uint8(offset + 2);
    }

    default int int32(int offset) {
        return uint8(offset) << 24 | uint8(offset + 1) << 16 | uint8(offset + 2) << 8 | uint8(offset + 3);
    }

    default int uint16(int offset) {
        return uint8(offset) << 8 | uint8(offset + 1);
    }

    default int uint8(int offset) {
        return get(offset) & 0xff;
    }

    default ByteArray int24(int offset, int val) {
        set(offset, (byte) ((val >> 16) & 0xff));
        set(offset + 1, (byte) ((val >> 8) & 0xff));
        set(offset + 2, (byte) ((val) & 0xff));
        return this;
    }

    default ByteArray int16(int offset, int val) {
        set(offset, (byte) ((val >> 8) & 0xff));
        set(offset + 1, (byte) ((val) & 0xff));
        return this;
    }

    default ByteArray int32(int offset, int val) {
        set(offset, (byte) ((val >> 24) & 0xff));
        set(offset + 1, (byte) ((val >> 16) & 0xff));
        set(offset + 2, (byte) ((val >> 8) & 0xff));
        set(offset + 3, (byte) ((val) & 0xff));
        return this;
    }

    /**
     * <pre>
     *      for (int i = off; i < off + len; i++)
     *          dst.put(this[i]);
     * </pre>
     */
    void byteBufferPut(ByteBuffer dst, int off, int len);

    /**
     * <pre>
     *      for (int i = off; i < off + len; i++)
     *          this[i] = src.get();
     * </pre>
     */
    void byteBufferGet(ByteBuffer src, int off, int len);
}
