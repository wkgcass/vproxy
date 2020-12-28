package vfd.abs;

import vfd.FD;
import vproxybase.util.ByteBufferEx;
import vproxybase.util.direct.DirectByteBuffer;
import vproxybase.util.direct.DirectMemoryUtils;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class AbstractBaseFD implements FD {
    private DirectByteBuffer directBufferForReading = null;
    private DirectByteBuffer directBufferForWriting = null;

    @SuppressWarnings("DuplicatedCode")
    protected ByteBufferEx getDirectBufferForReading(int len) {
        if (directBufferForReading != null && directBufferForReading.capacity() < len) {
            directBufferForReading.clean();
            directBufferForReading = null;
        }
        if (directBufferForReading == null) {
            directBufferForReading = DirectMemoryUtils.allocateDirectBuffer(2 * len);
        }
        return directBufferForReading;
    }

    @SuppressWarnings("DuplicatedCode")
    protected ByteBufferEx getDirectBufferForWriting(int len) {
        if (directBufferForWriting != null && directBufferForWriting.capacity() < len) {
            directBufferForWriting.clean();
            directBufferForWriting = null;
        }
        if (directBufferForWriting == null) {
            directBufferForWriting = DirectMemoryUtils.allocateDirectBuffer(2 * len);
        }
        return directBufferForWriting;
    }

    protected void resetDirectBufferForReading() {
        if (directBufferForReading == null) {
            return;
        }
        directBufferForReading.limit(directBufferForReading.capacity()).position(0);
    }

    protected void resetDirectBufferForWriting() {
        if (directBufferForWriting == null) {
            return;
        }
        directBufferForWriting.limit(directBufferForWriting.capacity()).position(0);
    }

    @Override
    public void close() throws IOException {
        if (directBufferForReading != null) {
            directBufferForReading.clean();
            directBufferForReading = null;
        }
        if (directBufferForWriting != null) {
            directBufferForWriting.clean();
            directBufferForWriting = null;
        }
    }

    @FunctionalInterface
    protected interface ReadFunc {
        int read(ByteBuffer directBuffer, int off, int len) throws IOException;
    }

    protected int utilRead(ByteBuffer dst, ReadFunc f) throws IOException {
        int off = 0;
        int len = dst.limit() - dst.position();
        ByteBuffer directBuffer;
        boolean needCopy = false;
        if (dst.isDirect()) {
            directBuffer = dst;
            off = dst.position();
        } else {
            directBuffer = getDirectBufferForReading(len).realBuffer();
            needCopy = true;
        }
        int n = 0;
        try {
            n = f.read(directBuffer, off, len);
        } finally {
            if (n > 0) {
                if (needCopy) {
                    directBuffer.limit(n).position(0);
                    dst.put(directBuffer);
                } else {
                    dst.position(dst.position() + n);
                }
            }
            resetDirectBufferForReading();
        }
        return n;
    }

    @FunctionalInterface
    protected interface WriteFunc {
        int write(ByteBuffer directBuffer, int off, int len) throws IOException;
    }

    protected int utilWrite(ByteBuffer src, WriteFunc f) throws IOException {
        int off = 0;
        int len = src.limit() - src.position();
        ByteBuffer directBuffer;
        boolean needCopy = false;
        if (src.isDirect()) {
            directBuffer = src;
            off = src.position();
        } else {
            directBuffer = getDirectBufferForWriting(len).realBuffer();
            directBuffer.put(src);
            needCopy = true;
        }
        int n = 0;
        try {
            n = f.write(directBuffer, off, len);
        } finally {
            if (needCopy) { // src was fully read
                if (n < len) {
                    src.position(src.limit() - len + n);
                }
            } else { // src was not modified
                if (n > 0) {
                    src.position(src.position() + n);
                }
            }
            resetDirectBufferForWriting();
        }
        return n;
    }
}
