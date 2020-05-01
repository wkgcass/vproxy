package vfd.windows;

import vfd.FD;
import vfd.TapDatagramFD;
import vfd.TapInfo;
import vfd.abs.AbstractBaseFD;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;

public class WindowsTapDatagramFD extends AbstractBaseFD implements TapDatagramFD {
    public final long handle;
    public final TapInfo tap;
    private final Windows windows;

    private boolean closed = false;

    public WindowsTapDatagramFD(Windows windows, long handle, TapInfo tap) {
        this.handle = handle;
        this.tap = tap;
        this.windows = windows;
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
    public void connect(InetSocketAddress l4addr) throws IOException {
        throw new IOException(new UnsupportedOperationException());
    }

    @Override
    public void bind(InetSocketAddress l4addr) throws IOException {
        throw new IOException(new UnsupportedOperationException());
    }

    @Override
    public int send(ByteBuffer buf, InetSocketAddress remote) throws IOException {
        throw new IOException(new UnsupportedOperationException());
    }

    @Override
    public SocketAddress receive(ByteBuffer buf) throws IOException {
        throw new IOException(new UnsupportedOperationException());
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        throw new IOException(new UnsupportedOperationException());
    }

    @Override
    public SocketAddress getRemoteAddress() throws IOException {
        throw new IOException(new UnsupportedOperationException());
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        checkNotClosed();

        return utilRead(dst, (buf, off, len) -> windows.read(handle, buf, off, len));
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        checkNotClosed();

        return utilWrite(src, (buf, off, len) -> windows.write(handle, buf, off, len));
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
        windows.closeHandle(handle);
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
