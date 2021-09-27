package io.vproxy.vfd.posix;

import java.io.IOException;
import java.nio.ByteBuffer;

public class PosixNetworkFD extends PosixFD {
    protected boolean connected = false;

    protected PosixNetworkFD(Posix posix) {
        super(posix);
    }

    public boolean isConnected() {
        return connected;
    }

    protected void checkConnected() throws IOException {
        if (!connected) {
            throw new IOException("not connected");
        }
    }

    public int read(ByteBuffer dst) throws IOException {
        checkFD();
        checkConnected();
        checkNotClosed();

        return utilRead(dst, (buf, off, len) -> posix.read(fd, buf, off, len));
    }

    public int write(ByteBuffer src) throws IOException {
        checkFD();
        checkConnected();
        checkNotClosed();

        return utilWrite(src, (buf, off, len) -> posix.write(fd, buf, off, len));
    }
}
