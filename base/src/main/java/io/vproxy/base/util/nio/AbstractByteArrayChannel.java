package io.vproxy.base.util.nio;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Utils;

public abstract class AbstractByteArrayChannel implements ByteArrayChannel {
    protected int writeOff; // the current write offset
    protected int writeLen; // free space left for writing into arr
    protected int readOff; // the current read offset

    protected final int initialWriteOff;
    protected final int initialWriteLen;
    protected final int initialReadOff;

    protected AbstractByteArrayChannel(int initialWriteOff, int initialWriteLen, int initialReadOff) {
        this.initialWriteOff = initialWriteOff;
        this.initialWriteLen = initialWriteLen;
        this.initialReadOff = initialReadOff;
        reset();
    }

    @Override
    public void reset() {
        this.writeOff = initialWriteOff;
        this.writeLen = initialWriteLen;
        this.readOff = initialReadOff;
    }

    @Override
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
    public int write(ByteArrayChannel src, int len) {
        if (len < 0) {
            throw new IllegalArgumentException("len = " + len + " < 0");
        }
        if (src.used() < len) {
            throw new IndexOutOfBoundsException("src.used = " + src.used() + ", len = " + len + " < 0");
        }

        int writeBytes = Math.min(writeLen, len);
        var tmp = Utils.allocateByteBuffer(len);
        src.read(tmp);
        tmp.flip();
        write(tmp);

        writeOff += writeBytes;
        writeLen -= writeBytes;
        return writeBytes;
    }

    @Override
    public int free() {
        return writeLen;
    }

    @Override
    public int used() {
        return writeOff - readOff;
    }

    @Override
    public ByteArray readAll() {
        ByteArray arr = readableArray();
        readOff = writeOff; // everything read
        return arr;
    }

    @Override
    public int getWriteOff() {
        return writeOff - initialReadOff;
    }

    @Override
    public int getWriteLen() {
        return writeLen;
    }

    @Override
    public int getReadOff() {
        return readOff - initialReadOff;
    }

    @Override
    public void setReadOff(int readOff) {
        if (readOff < 0)
            throw new IllegalArgumentException();
        if (readOff > getWriteOff())
            throw new IllegalArgumentException();
        this.readOff = initialReadOff + readOff;
    }

    @Override
    public String toString() {
        return "ByteArrayChannel" + "(" + readableArray().toString().replaceAll("\\r|\\n", ".") + ")";
    }
}
