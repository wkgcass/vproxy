package vproxybase.util;

import vproxybase.util.bytearray.*;
import vproxybase.util.nio.ByteArrayChannel;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

@SuppressWarnings("UnusedReturnValue")
public interface ByteArray {
    static ByteArray allocate(int len) {
        if (len == 0) {
            return AbstractByteArray.EMPTY;
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

    static ByteArray from(ByteBuffer buf) {
        int len = buf.limit() - buf.position();
        if (buf.hasArray()) {
            return from(buf.array()).sub(buf.position(), len);
        } else {
            byte[] array = new byte[len];
            buf.get(array);
            return from(array);
        }
    }

    static ByteArray from(int... array) {
        byte[] bytes = new byte[array.length];
        for (int i = 0; i < array.length; ++i) {
            bytes[i] = (byte) array[i];
        }
        return from(bytes);
    }

    static ByteArray from(String str) {
        return from(str.getBytes());
    }

    static ByteArray fromHexString(String str) throws IllegalArgumentException {
        return from(Utils.hexToBytes(str));
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
        toNewJavaArray(array, 0);
        return array;
    }

    void toNewJavaArray(byte[] holder, int off);

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

    default long int64(int offset) {
        return uint8long(offset) << 56
            | uint8long(offset + 1) << 48
            | uint8long(offset + 2) << 40
            | uint8long(offset + 3) << 32
            | uint8long(offset + 4) << 24
            | uint8long(offset + 5) << 16
            | uint8long(offset + 6) << 8
            | uint8long(offset + 7);
    }

    default int int32(int offset) {
        return uint8(offset) << 24 | uint8(offset + 1) << 16 | uint8(offset + 2) << 8 | uint8(offset + 3);
    }

    default long uint32(int offset) {
        return (((long) uint8(offset)) << 24) | (((long) uint8(offset + 1)) << 16) | (((long) uint8(offset + 2)) << 8) | (((long) uint8(offset + 3)));
    }

    default int uint16(int offset) {
        return uint8(offset) << 8 | uint8(offset + 1);
    }

    default int uint8(int offset) {
        return get(offset) & 0xff;
    }

    default long uint8long(int offset) {
        return get(offset) & 0xFFFFFFFFL;
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

    default ByteArray int64(int offset, long val) {
        set(offset, (byte) ((val >> 56) & 0xff));
        set(offset + 1, (byte) ((val >> 48) & 0xff));
        set(offset + 2, (byte) ((val >> 40) & 0xff));
        set(offset + 3, (byte) ((val >> 32) & 0xff));
        set(offset + 4, (byte) ((val >> 24) & 0xff));
        set(offset + 5, (byte) ((val >> 16) & 0xff));
        set(offset + 6, (byte) ((val >> 8) & 0xff));
        set(offset + 7, (byte) ((val) & 0xff));
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

    default String toHexString() {
        return Utils.bytesToHex(toJavaArray());
    }

    default byte[] toGZipJavaByteArray() {
        byte[] dataToCompress = toJavaArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        return Utils.gzipCompress(out, dataToCompress);
    }
}
