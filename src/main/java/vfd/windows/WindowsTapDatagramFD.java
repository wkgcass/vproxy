package vfd.windows;

import vfd.FD;
import vfd.NoSockAddr;
import vfd.TapDatagramFD;
import vfd.TapInfo;
import vfd.abs.AbstractBaseFD;
import vproxy.util.Logger;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.ByteBuffer;

public class WindowsTapDatagramFD extends AbstractBaseFD implements TapDatagramFD {
    public final long handle;
    public final TapInfo tap;
    private final long readOverlapped;
    private final long writeOverlapped;
    private final Windows windows;

    private boolean closed = false;
    private boolean handleClosed = false;
    private boolean readOverlappedReleased = false;
    private boolean writeOverlappedReleased = false;

    public WindowsTapDatagramFD(Windows windows, long handle, TapInfo tap, long readOverlapped, long writeOverlapped) {
        this.handle = handle;
        this.tap = tap;
        this.windows = windows;
        this.readOverlapped = readOverlapped;
        this.writeOverlapped = writeOverlapped;
    }

    @Override
    public TapInfo getTap() {
        return tap;
    }

    protected void checkNotClosed() throws IOException {
        if (closed) {
            throw new IOException("closed");
        }
    }

    @Override
    public void connect(NoSockAddr l4addr) throws IOException {
        throw new IOException(new UnsupportedOperationException());
    }

    @Override
    public void bind(NoSockAddr l4addr) throws IOException {
        throw new IOException(new UnsupportedOperationException());
    }

    @Override
    public int send(ByteBuffer buf, NoSockAddr remote) throws IOException {
        throw new IOException(new UnsupportedOperationException());
    }

    @Override
    public NoSockAddr receive(ByteBuffer buf) throws IOException {
        throw new IOException(new UnsupportedOperationException());
    }

    @Override
    public NoSockAddr getLocalAddress() throws IOException {
        throw new IOException(new UnsupportedOperationException());
    }

    @Override
    public NoSockAddr getRemoteAddress() throws IOException {
        throw new IOException(new UnsupportedOperationException());
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        checkNotClosed();

        return utilRead(dst, (buf, off, len) -> windows.read(handle, buf, off, len, readOverlapped));
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        checkNotClosed();

        int srcLen = src.limit() - src.position();
        int n = utilWrite(src, (buf, off, len) -> windows.write(handle, buf, off, len, writeOverlapped));
        if (n == 0) {
            assert Logger.lowLevelDebug("windows tap driver may reject packets not belong to this endpoint (e.g. ipv6 multicast) and return 0, we need to skip this packet");
            return srcLen;
        }
        return n;
    }

    @Override
    public void configureBlocking(boolean b) throws IOException {
        throw new IOException(new UnsupportedOperationException());
    }

    @Override
    public <T> void setOption(SocketOption<T> name, T value) throws IOException {
        throw new IOException(new UnsupportedOperationException());
    }

    @Override
    public FD real() {
        return this;
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        if (!handleClosed) {
            windows.closeHandle(handle);
            handleClosed = true;
        }
        if (!readOverlappedReleased) {
            windows.releaseOverlapped(readOverlapped);
            readOverlappedReleased = true;
        }
        if (!writeOverlappedReleased) {
            windows.releaseOverlapped(writeOverlapped);
            writeOverlappedReleased = true;
        }
        closed = true;
    }

    @Override
    public String toString() {
        return "WindowsTapDatagramFD{" +
            "handle=" + handle +
            ", tap=" + tap +
            '}';
    }
}
