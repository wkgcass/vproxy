package io.vproxy.vfd.posix;

import io.vproxy.vfd.FD;
import io.vproxy.vfd.SocketOptions;
import io.vproxy.vfd.abs.AbstractBaseFD;

import java.io.IOException;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.util.HashMap;
import java.util.Map;

public class PosixFD extends AbstractBaseFD implements FD {
    protected final Posix posix;
    private boolean closed = false;
    protected int fd = -1;
    private Boolean blocking = null;
    @SuppressWarnings("rawtypes")
    private final Map<SocketOption, Object> opts = new HashMap<>();

    protected PosixFD(Posix posix) {
        this.posix = posix;
    }

    public int getFD() {
        return fd;
    }

    protected void checkFD() throws IOException {
        if (fd == -1) {
            throw new IOException("connect() or bind() not called");
        }
    }

    protected void checkNotClosed() throws IOException {
        if (closed) {
            throw new IOException("closed");
        }
    }

    @Override
    public void configureBlocking(boolean b) throws IOException {
        checkNotClosed();
        if (fd == -1) {
            blocking = b;
        } else {
            posix.setBlocking(fd, b);
        }
    }

    protected void finishConfigAfterFDCreated() throws IOException {
        if (blocking != null) {
            configureBlocking(blocking);
        }
        for (var entry : opts.entrySet()) {
            //noinspection unchecked
            setOption(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public <T> void setOption(SocketOption<T> name, T value) throws IOException {
        checkNotClosed();
        if (fd == -1) {
            if (name != StandardSocketOptions.SO_LINGER
                && name != StandardSocketOptions.SO_REUSEPORT
                && name != StandardSocketOptions.TCP_NODELAY
                && name != StandardSocketOptions.SO_RCVBUF
                && name != StandardSocketOptions.SO_BROADCAST
                && name != SocketOptions.IP_TRANSPARENT) {
                throw new IOException("not supported " + name);
            }
            opts.put(name, value);
        } else {
            if (name == StandardSocketOptions.SO_LINGER) {
                posix.setSoLinger(fd, (Integer) value);
            } else if (name == StandardSocketOptions.SO_REUSEPORT) {
                posix.setReusePort(fd, (Boolean) value);
            } else if (name == StandardSocketOptions.SO_RCVBUF) {
                posix.setRcvBuf(fd, (Integer) value);
            } else if (name == StandardSocketOptions.TCP_NODELAY) {
                posix.setTcpNoDelay(fd, (Boolean) value);
            } else if (name == StandardSocketOptions.SO_BROADCAST) {
                posix.setBroadcast(fd, (Boolean) value);
            } else if (name == SocketOptions.IP_TRANSPARENT) {
                posix.setIpTransparent(fd, (Boolean) value);
            } else {
                throw new IOException("not supported " + name);
            }
        }
    }

    @Override
    public FD real() {
        return this;
    }

    @Override
    public boolean contains(FD fd) {
        return false;
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            super.close();
            return;
        }
        closed = true;
        if (fd != -1) {
            posix.close(fd);
        }
        super.close();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" +
            "fd=" + fd +
            ", closed=" + closed +
            '}';
    }
}
