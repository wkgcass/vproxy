package net.cassite.vproxy.util;

import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class ByteArrayChannel implements ReadableByteChannel, WritableByteChannel {
    private final byte[] arr;
    private int writeOff; // the current write offset
    private int writeLen; // free space left for writing into arr
    private int readOff; // the current read offset

    private final int initialOff;
    private final int initialLen;
    private final int initialReadOff;

    public ByteArrayChannel(byte[] arr) {
        this(arr, 0, 0, arr.length);
    }

    public ByteArrayChannel(byte[] arr, int readOff, int writeOff, int writeLen) {
        this.arr = arr;
        if (writeOff > arr.length || writeOff + writeLen > arr.length)
            throw new IllegalArgumentException();
        this.initialOff = writeOff;
        this.initialLen = writeLen;
        this.initialReadOff = readOff;

        reset();
    }

    @Override
    public int read(ByteBuffer dst) {
        int readLen = writeOff - readOff;
        int readBytes = Math.min(readLen, dst.limit() - dst.position());
        dst.put(arr, readOff, readBytes);
        readOff += readBytes;
        return readBytes;
    }

    @Override
    public int write(ByteBuffer src) {
        int writeBytes = Math.min(writeLen, src.limit() - src.position());
        src.get(arr, writeOff, writeBytes);
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
}
