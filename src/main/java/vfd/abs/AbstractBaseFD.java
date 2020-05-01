package vfd.abs;

import vfd.FD;
import vproxy.util.Utils;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class AbstractBaseFD implements FD {
    private ByteBuffer directBuffer = null;

    protected ByteBuffer getDirectBuffer(int len) {
        if (directBuffer != null && directBuffer.capacity() < len) {
            Utils.clean(directBuffer);
            directBuffer = null;
        }
        if (directBuffer == null) {
            directBuffer = ByteBuffer.allocateDirect(2 * len);
        }
        return directBuffer;
    }

    protected void resetDirectBuffer() {
        if (directBuffer == null) {
            return;
        }
        directBuffer.limit(directBuffer.capacity()).position(0);
    }

    @Override
    public void close() throws IOException {
        if (directBuffer != null) {
            Utils.clean(directBuffer);
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
            directBuffer = getDirectBuffer(len);
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
            resetDirectBuffer();
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
            directBuffer = getDirectBuffer(len);
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
            resetDirectBuffer();
        }
        return n;
    }
}
