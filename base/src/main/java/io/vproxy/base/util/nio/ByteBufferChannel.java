package io.vproxy.base.util.nio;

import io.vproxy.base.util.ByteArray;

import java.nio.ByteBuffer;

public class ByteBufferChannel extends AbstractByteArrayChannel implements ByteArrayChannel {
    private final ByteBuffer buf;

    ByteBufferChannel(ByteBuffer buf, int readOff, int writeOff, int writeLen) {
        super(writeOff, writeLen, readOff);
        this.buf = buf;
    }

    @Override
    public int read(ByteBuffer dst) {
        int readLen = writeOff - readOff;
        int readBytes = Math.min(readLen, dst.limit() - dst.position());

        int oldDstLimit = dst.limit();
        dst.limit(dst.position() + readBytes);

        buf.limit(readOff + readBytes).position(readOff);
        dst.put(buf);

        dst.limit(oldDstLimit);

        readOff += readBytes;
        return readBytes;
    }

    @Override
    public byte read() {
        if (writeOff - readOff == 0) {
            throw new IndexOutOfBoundsException("readOff=" + readOff + ", writeOff=" + writeOff);
        }
        return buf.get(readOff++);
    }

    @Override
    public int write(ByteBuffer src) {
        int writeBytes = Math.min(writeLen, src.limit() - src.position());

        int oldSrcLimit = src.limit();
        src.limit(src.position() + writeBytes);

        buf.limit(writeOff + writeBytes).position(writeOff);
        buf.put(src);
        src.limit(oldSrcLimit);

        writeOff += writeBytes;
        writeLen -= writeBytes;
        return writeBytes;
    }

    @Override
    public ByteArray readableArray() {
        int off = readOff;
        int len = writeOff - readOff;
        if (buf.hasArray()) {
            var arr = buf.array();
            return ByteArray.from(arr).sub(off, len);
        } else {
            var ret = ByteArray.allocate(len);
            buf.limit(writeOff).position(readOff);
            ret.byteBufferGet(buf, 0, len);
            return ret;
        }
    }

    @Override
    public ByteArray getArray() {
        int off = initialReadOff;
        int len = initialWriteOff + initialWriteLen - initialReadOff;
        if (buf.hasArray()) {
            var arr = buf.array();
            return ByteArray.from(arr).sub(off, len);
        } else {
            var ret = ByteArray.allocate(len);
            buf.limit(initialWriteOff).position(initialReadOff);
            ret.byteBufferGet(buf, 0, len);
            return ret;
        }
    }
}
