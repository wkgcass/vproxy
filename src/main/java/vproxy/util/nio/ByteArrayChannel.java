package vproxy.util.nio;

import vproxy.util.ByteArray;

import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class ByteArrayChannel implements ReadableByteChannel, WritableByteChannel {
    private final ByteArray arr;
    private int writeOff; // the current write offset
    private int writeLen; // free space left for writing into arr
    private int readOff; // the current read offset

    private final int initialWriteOff;
    private final int initialWriteLen;
    private final int initialReadOff;

    private ByteArrayChannel(ByteArray arr, int readOff, int writeOff, int writeLen) {
        this.arr = arr;
        if (arr.length() == 0 ||
            readOff >= arr.length() ||
            readOff < 0 ||
            writeOff < 0 ||
            writeOff > arr.length() ||
            writeLen < 0 ||
            writeOff + writeLen > arr.length())
            throw new IllegalArgumentException();
        this.initialWriteOff = writeOff;
        this.initialWriteLen = writeLen;
        this.initialReadOff = readOff;

        reset();
    }

    private ByteArrayChannel() {
        this.arr = ByteArray.from(new byte[0]);
        this.initialWriteOff = 0;
        this.initialWriteLen = 0;
        this.initialReadOff = 0;
    }

    public static ByteArrayChannel fromEmpty(int len) {
        return new ByteArrayChannel(ByteArray.allocate(len), 0, 0, len);
    }

    public static ByteArrayChannel fromEmpty(byte[] arr) {
        return new ByteArrayChannel(ByteArray.from(arr), 0, 0, arr.length);
    }

    public static ByteArrayChannel fromFull(byte[] arr) {
        return new ByteArrayChannel(ByteArray.from(arr), 0, arr.length, 0);
    }

    public static ByteArrayChannel from(byte[] arr, int readOff, int writeOff, int writeLen) {
        return from(ByteArray.from(arr), readOff, writeOff, writeLen);
    }

    public static ByteArrayChannel from(ByteArray arr, int readOff, int writeOff, int writeLen) {
        return new ByteArrayChannel(arr, readOff, writeOff, writeLen);
    }

    public static ByteArrayChannel fromFull(ByteArray arr) {
        return new ByteArrayChannel(arr, 0, arr.length(), 0);
    }

    public static ByteArrayChannel zero() {
        return new ByteArrayChannel();
    }

    @Override
    public int read(ByteBuffer dst) {
        int readLen = writeOff - readOff;
        int readBytes = Math.min(readLen, dst.limit() - dst.position());
        arr.byteBufferPut(dst, readOff, readBytes);
        readOff += readBytes;
        return readBytes;
    }

    public byte read() {
        if (writeOff - readOff == 0) {
            throw new IndexOutOfBoundsException("readOff=" + readOff + ", writeOff=" + writeOff);
        }
        return arr.get(readOff++);
    }

    public void skip(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n = " + n + " < 0");
        }

        if (readOff + n > writeOff) {
            throw new IndexOutOfBoundsException("readOff + n = " + readOff + " + " + n + " = " + (readOff + n) + " > writeOff(" + writeOff + ")");
        }
        readOff += n;
    }

    @Override
    public int write(ByteBuffer src) {
        int writeBytes = Math.min(writeLen, src.limit() - src.position());
        arr.byteBufferGet(src, writeOff, writeBytes);
        writeOff += writeBytes;
        writeLen -= writeBytes;
        return writeBytes;
    }

    public int write(ByteArrayChannel src) {
        return write(src, src.used());
    }

    public int write(ByteArrayChannel src, int len) {
        if (len < 0) {
            throw new IllegalArgumentException("len = " + len + " < 0");
        }
        if (src.used() < len) {
            throw new IndexOutOfBoundsException("src.used = " + src.used() + ", len = " + len + " < 0");
        }

        int writeBytes = Math.min(writeLen, len);
        ByteBuffer tmp = ByteBuffer.allocate(len);
        src.read(tmp);
        tmp.flip();
        arr.byteBufferGet(tmp, writeOff, writeBytes);
        writeOff += writeBytes;
        writeLen -= writeBytes;
        return writeBytes;
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public void close() {
        // we do not close this channel
    }

    public int free() {
        return writeLen;
    }

    public int used() {
        return writeOff - readOff;
    }

    public void reset() {
        this.writeOff = initialWriteOff;
        this.writeLen = initialWriteLen;
        this.readOff = initialReadOff;
    }

    public byte[] getBytes() {
        return getArray().toJavaArray();
    }

    public ByteArray readableArray() {
        int off = readOff;
        int len = writeOff - readOff;
        if (off == 0 && len == arr.length()) {
            return arr;
        } else {
            return arr.sub(off, len);
        }
    }

    public ByteArray readAll() {
        ByteArray arr = readableArray();
        readOff = writeOff; // everything read
        return arr;
    }

    public ByteArray getArray() {
        int off = initialReadOff;
        int len = initialWriteOff + initialWriteLen - initialReadOff;
        if (off == 0 && len == arr.length()) {
            return arr;
        } else {
            return arr.sub(off, len);
        }
    }

    public int getWriteOff() {
        return writeOff - initialReadOff;
    }

    public int getWriteLen() {
        return writeLen - initialReadOff;
    }

    public int getReadOff() {
        return readOff - initialReadOff;
    }

    @Override
    public String toString() {
        return "ByteArrayChannel:" + arr.getClass().getSimpleName() + "(" + arr.sub(readOff, writeOff - readOff).toString().replaceAll("\\r|\\n", ".") + ")";
    }
}
