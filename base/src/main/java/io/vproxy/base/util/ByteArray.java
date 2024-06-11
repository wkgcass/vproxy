package io.vproxy.base.util;

import io.vproxy.base.util.bytearray.*;
import io.vproxy.base.util.nio.ByteArrayChannel;

import java.io.ByteArrayOutputStream;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

@SuppressWarnings("UnusedReturnValue")
public interface ByteArray extends ToByteArray {
    static ByteArray allocate(int len) {
        if (len == 0) {
            return AbstractByteArray.EMPTY;
        } else if (len == 1) {
            return new OneByteArray();
        } else {
            return from(Utils.allocateByteArray(len));
        }
    }

    static ByteArray allocateInitZero(int len) {
        if (len == 0 || len == 1) {
            return allocate(len);
        } else {
            return from(Utils.allocateByteArrayInitZero(len));
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
            byte[] array = Utils.allocateByteArray(len);
            buf.get(array);
            return from(array);
        }
    }

    static ByteArray from(int... array) {
        byte[] bytes = Utils.allocateByteArray(array.length);
        for (int i = 0; i < array.length; ++i) {
            bytes[i] = (byte) array[i];
        }
        return from(bytes);
    }

    static ByteArray from(String str) {
        return from(str.getBytes());
    }

    static ByteArray from(MemorySegment seg) {
        return new MemorySegmentByteArray(seg);
    }

    static ByteArray fromHexString(String str) throws IllegalArgumentException {
        return from(Utils.hexToBytes(str));
    }

    static ByteArray fromBinString(String str) throws IllegalArgumentException {
        return from(Utils.binToBytes(str));
    }

    byte get(int idx);

    ByteArray set(int idx, byte value);

    int length();

    default ByteArray sub(int fromInclusive, int len) {
        return SubByteArray.sub(this, fromInclusive, len);
    }

    default ByteArray concat(ByteArray array) {
        if (array.length() == 0) {
            return this;
        }
        if (length() == 0)
            return array;
        return new CompositeByteArray(this, array);
    }

    default ByteArray concat(String str) {
        if (str.isEmpty())
            return this;
        return concat(ByteArray.from(str));
    }

    default ByteArray toByteArray() {
        return this;
    }

    default byte[] toJavaArray() {
        return toNewJavaArray();
    }

    default byte[] toNewJavaArray() {
        int len = length();
        byte[] array = Utils.allocateByteArray(len);
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

    default ByteArray unmodifiable() {
        return new UnmodifiableByteArray(this);
    }

    default ByteArray persist() {
        return new PersistByteArray(this);
    }

    default ByteArrayChannel toFullChannel() {
        return ByteArrayChannel.fromFull(this);
    }

    default int uint24(int offset) {
        return uint8(offset) << 16 | uint8(offset + 1) << 8 | uint8(offset + 2);
    }

    default int uint24ReverseNetworkByteOrder(int offset) {
        return uint8(offset + 2) << 16 | uint8(offset + 1) << 8 | uint8(offset);
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

    default long int64ReverseNetworkByteOrder(int offset) {
        return uint8long(offset + 7) << 56
            | uint8long(offset + 6) << 48
            | uint8long(offset + 5) << 40
            | uint8long(offset + 4) << 32
            | uint8long(offset + 3) << 24
            | uint8long(offset + 2) << 16
            | uint8long(offset + 1) << 8
            | uint8long(offset);
    }

    default int int32(int offset) {
        return uint8(offset) << 24 | uint8(offset + 1) << 16 | uint8(offset + 2) << 8 | uint8(offset + 3);
    }

    default int int32ReverseNetworkByteOrder(int offset) {
        return uint8(offset + 3) << 24 | uint8(offset + 2) << 16 | uint8(offset + 1) << 8 | uint8(offset);
    }

    default long uint32(int offset) {
        return (((long) uint8(offset)) << 24) | (((long) uint8(offset + 1)) << 16) | (((long) uint8(offset + 2)) << 8) | (((long) uint8(offset + 3)));
    }

    default long uint32ReverseNetworkByteOrder(int offset) {
        return (((long) uint8(offset + 3)) << 24) | (((long) uint8(offset + 2)) << 16) | (((long) uint8(offset + 1)) << 8) | (((long) uint8(offset)));
    }

    default int uint16(int offset) {
        return uint8(offset) << 8 | uint8(offset + 1);
    }

    default int uint16ReverseNetworkByteOrder(int offset) {
        return uint8(offset + 1) << 8 | uint8(offset);
    }

    default int uint8(int offset) {
        return get(offset) & 0xff;
    }

    default long uint8long(int offset) {
        return get(offset) & 0xffL;
    }

    default ByteArray int24(int offset, int val) {
        set(offset, (byte) ((val >> 16) & 0xff));
        set(offset + 1, (byte) ((val >> 8) & 0xff));
        set(offset + 2, (byte) ((val) & 0xff));
        return this;
    }
    default ByteArray int24ReverseNetworkByteOrder(int offset, int val) {
        set(offset, (byte) ((val) & 0xff));
        set(offset + 1, (byte) ((val >> 8) & 0xff));
        set(offset + 2, (byte) ((val >> 16) & 0xff));
        return this;
    }

    default ByteArray int16(int offset, int val) {
        set(offset, (byte) ((val >> 8) & 0xff));
        set(offset + 1, (byte) ((val) & 0xff));
        return this;
    }

    default ByteArray int16ReverseNetworkByteOrder(int offset, int val) {
        set(offset, (byte) ((val) & 0xff));
        set(offset + 1, (byte) ((val >> 8) & 0xff));
        return this;
    }

    default ByteArray int32(int offset, int val) {
        set(offset, (byte) ((val >> 24) & 0xff));
        set(offset + 1, (byte) ((val >> 16) & 0xff));
        set(offset + 2, (byte) ((val >> 8) & 0xff));
        set(offset + 3, (byte) ((val) & 0xff));
        return this;
    }

    default ByteArray int32ReverseNetworkByteOrder(int offset, int val) {
        set(offset, (byte) ((val) & 0xff));
        set(offset + 1, (byte) ((val >> 8) & 0xff));
        set(offset + 2, (byte) ((val >> 16) & 0xff));
        set(offset + 3, (byte) ((val >> 24) & 0xff));
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

    default ByteArray int64ReverseNetworkByteOrder(int offset, long val) {
        set(offset, (byte) ((val) & 0xff));
        set(offset + 1, (byte) ((val >> 8) & 0xff));
        set(offset + 2, (byte) ((val >> 16) & 0xff));
        set(offset + 3, (byte) ((val >> 24) & 0xff));
        set(offset + 4, (byte) ((val >> 32) & 0xff));
        set(offset + 5, (byte) ((val >> 40) & 0xff));
        set(offset + 6, (byte) ((val >> 48) & 0xff));
        set(offset + 7, (byte) ((val >> 56) & 0xff));
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

    default void copyInto(ByteArray dst, int dstOff, int srcOff, int srcLen) {
        for (int i = 0; i < srcLen; ++i) {
            dst.set(dstOff + i, get(srcOff + i));
        }
    }

    default String toHexString() {
        return Utils.bytesToHex(toJavaArray());
    }

    default String hexDump() {
        return hexDump(16);
    }

    default String hexDump(int bytesPerLine) {
        var result = new StringBuilder();

        var sb = new StringBuilder();
        for (int i = 0, size = this.length(); i < size; i += bytesPerLine) {
            sb.delete(0, sb.length());
            int j = 0;
            for (; j < bytesPerLine && i + j < size; ++j) {
                int n = this.get(i + j) & 0xff;
                var s = Integer.toString(n, 16);
                if (s.length() == 1) {
                    s = "0" + s;
                }
                result.append(s);
                result.append(" ");
                if (n >= 33 && n <= 126) {
                    sb.append((char) n);
                } else {
                    sb.append(".");
                }
            }
            result.append(" ".repeat(7));
            if (bytesPerLine > j) {
                result.append(" ".repeat((bytesPerLine - j) * 3));
            }
            result.append(sb).append("\n");
        }

        return result.toString();
    }

    default byte[] toGZipJavaByteArray() {
        byte[] dataToCompress = toJavaArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        return Utils.gzipCompress(out, dataToCompress);
    }

    private byte upperByte(byte b) {
        return 'a' <= b && b <= 'z' ? (byte) (b - ('a' - 'A')) : b;
    }

    default ByteArray trim() {
        int len = this.length();
        int start = 0;
        while (start < len && Character.isWhitespace((char) this.get(start))) {
            start++;
        }

        if (len == start) {
            return AbstractByteArray.EMPTY;
        }

        int end = length() - 1;
        while (end >= 0 && Character.isWhitespace((char) this.get(end))) {
            end--;
        }

        if (start == end) {
            return AbstractByteArray.EMPTY;
        }

        return this.sub(start, end - start + 1);
    }

    default boolean equalsIgnoreCase(ByteArray other) {
        if (this.length() != other.length()) {
            return false;
        }

        for (int i = 0; i < length(); i++) {
            if (upperByte(this.get(i)) != upperByte(other.get(i))) {
                return false;
            }
        }
        return true;
    }

    default boolean equalsIgnoreCaseAfterTrim(ByteArray other) {
        return this.trim().equalsIgnoreCase(other.trim());
    }
}
