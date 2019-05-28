package net.cassite.vproxy.util;

import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class ByteArrayChannel implements ReadableByteChannel, WritableByteChannel {
    private final ByteArray arr;
    private int writeOff; // the current write offset
    private int writeLen; // free space left for writing into arr
    private int readOff; // the current read offset

    private final int initialOff;
    private final int initialLen;
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
        this.initialOff = writeOff;
        this.initialLen = writeLen;
        this.initialReadOff = readOff;

        reset();
    }

    public static ByteArrayChannel fromEmpty(byte[] arr) {
        return new ByteArrayChannel(ByteArray.from(arr), 0, 0, arr.length);
    }

    public static ByteArrayChannel fromFull(byte[] arr) {
        return new ByteArrayChannel(ByteArray.from(arr), 0, arr.length, 0);
    }

    public static ByteArrayChannel from(byte[] arr, int readOff, int writeOff, int writeLen) {
        return new ByteArrayChannel(ByteArray.from(arr), readOff, writeOff, writeLen);
    }

    public static ByteArrayChannel fromFull(ByteArray arr) {
        return new ByteArrayChannel(arr, 0, arr.length(), 0);
    }

    @Override
    public int read(ByteBuffer dst) {
        int readLen = writeOff - readOff;
        int readBytes = Math.min(readLen, dst.limit() - dst.position());
        arr.byteBufferPut(dst, readOff, readBytes);
        readOff += readBytes;
        return readBytes;
    }

    @Override
    public int write(ByteBuffer src) {
        int writeBytes = Math.min(writeLen, src.limit() - src.position());
        arr.byteBufferGet(src, writeOff, writeBytes);
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
        this.writeOff = initialOff;
        this.writeLen = initialLen;
        this.readOff = initialReadOff;
    }

    public byte[] get() {
        return arr.toJavaArray();
    }
}
