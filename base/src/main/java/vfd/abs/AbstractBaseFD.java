package vfd.abs;

import vfd.FD;
import vproxybase.util.Utils;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class AbstractBaseFD implements FD {
    private ByteBuffer directBufferForReading = null;
    private ByteBuffer directBufferForWriting = null;

    protected ByteBuffer getDirectBufferForReading(int len) {
        if (directBufferForReading != null && directBufferForReading.capacity() < len) {
            Utils.clean(directBufferForReading);
            directBufferForReading = null;
        }
        if (directBufferForReading == null) {
            directBufferForReading = ByteBuffer.allocateDirect(2 * len);
        }
        return directBufferForReading;
    }

    protected ByteBuffer getDirectBufferForWriting(int len) {
        if (directBufferForWriting != null && directBufferForWriting.capacity() < len) {
            Utils.clean(directBufferForWriting);
            directBufferForWriting = null;
        }
        if (directBufferForWriting == null) {
            directBufferForWriting = ByteBuffer.allocateDirect(2 * len);
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
            Utils.clean(directBufferForReading);
        }
        if (directBufferForWriting != null) {
            Utils.clean(directBufferForWriting);
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
            directBuffer = getDirectBufferForReading(len);
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
            directBuffer = getDirectBufferForWriting(len);
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
