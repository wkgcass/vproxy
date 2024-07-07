package io.vproxy.base.util.nio;

import io.vproxy.base.util.ByteArray;

import java.nio.ByteBuffer;

public class SimpleByteArrayChannel extends AbstractByteArrayChannel implements ByteArrayChannel {
    private final ByteArray arr;

    SimpleByteArrayChannel(ByteArray arr, int readOff, int writeOff, int writeLen) {
        super(writeOff, writeLen, readOff);
        this.arr = arr;
        if (arr.length() == 0 ||
            readOff >= arr.length() ||
            readOff < 0 ||
            writeOff < 0 ||
            writeOff > arr.length() ||
            writeLen < 0 ||
            writeOff + writeLen > arr.length())
            throw new IllegalArgumentException();
    }

    SimpleByteArrayChannel() {
        super(0, 0, 0);
        this.arr = ByteArray.allocate(0);
        reset();
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
    public byte read() {
        if (writeOff - readOff == 0) {
            throw new IndexOutOfBoundsException("readOff=" + readOff + ", writeOff=" + writeOff);
        }
        return arr.get(readOff++);
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
    public ByteArray readableArray() {
        int off = readOff;
        int len = writeOff - readOff;
        if (off == 0 && len == arr.length()) {
            return arr;
        } else {
            return arr.sub(off, len);
        }
    }

    @Override
    public ByteArray getArray() {
        int off = initialReadOff;
        int len = initialWriteOff + initialWriteLen - initialReadOff;
        if (off == 0 && len == arr.length()) {
            return arr;
        } else {
            return arr.sub(off, len);
        }
    }
}
