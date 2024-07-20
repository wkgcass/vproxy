package io.vproxy.vfd.windows;

import io.vproxy.vfd.EventSet;
import io.vproxy.vfd.FD;
import io.vproxy.vfd.abs.AbstractBaseFD;
import io.vproxy.vfd.posix.Posix;

import java.io.IOException;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.util.HashMap;
import java.util.Map;

public abstract class WindowsFD extends AbstractBaseFD implements FD {
    protected final Windows windows;
    protected final Posix posix;
    private boolean closed = false;
    protected WinSocket socket;
    @SuppressWarnings("rawtypes")
    private final Map<SocketOption, Object> opts = new HashMap<>();

    protected WindowsFD(Windows windows, Posix posix) {
        this.windows = windows;
        this.posix = posix;
    }

    public int getFD() {
        if (socket == null) {
            return -1;
        }
        return (int) socket.fd.MEMORY.address();
    }

    protected void checkFD() throws IOException {
        if (socket == null) {
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
        if (b) {
            throw new IOException("blocking socket is not supported");
        }
    }

    protected void setSocket(WinSocket socket) {
        if (this.socket != null)
            throw new IllegalStateException("already bond to a socket + " + this.socket + ", cannot bind to new socket " + socket);
        this.socket = socket;
        socket.ud = this;
    }

    protected void finishConfigAfterFDCreated() throws IOException {
        for (var entry : opts.entrySet()) {
            //noinspection unchecked
            setOption(entry.getKey(), entry.getValue());
        }
        opts.clear();
    }

    @Override
    public <T> void setOption(SocketOption<T> name, T value) throws IOException {
        checkNotClosed();
        if (socket == null) {
            if (name != StandardSocketOptions.SO_LINGER
                && name != StandardSocketOptions.TCP_NODELAY
                && name != StandardSocketOptions.SO_RCVBUF
                && name != StandardSocketOptions.SO_BROADCAST) {
                throw new IOException("not supported " + name);
            }
            opts.put(name, value);
        } else {
            if (name == StandardSocketOptions.SO_LINGER) {
                posix.setSoLinger(getFD(), (Integer) value);
            } else if (name == StandardSocketOptions.SO_RCVBUF) {
                posix.setRcvBuf(getFD(), (Integer) value);
            } else if (name == StandardSocketOptions.TCP_NODELAY) {
                posix.setTcpNoDelay(getFD(), (Boolean) value);
            } else if (name == StandardSocketOptions.SO_BROADCAST) {
                posix.setBroadcast(getFD(), (Boolean) value);
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
        if (socket != null) {
            socket.close();
        }
        super.close();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + ":" + socket;
    }

    // ============
    // similar to virtual fd

    IOCPSelector selector;
    boolean firingReadable;
    boolean firingWritable;
    EventSet watchingEvents = EventSet.none();
    Object attachment;

    protected void setReadable() {
        firingReadable = true;
        setEventsToSelector();
    }

    protected void setWritable() {
        firingWritable = true;
        setEventsToSelector();
    }

    private void setEventsToSelector() {
        var selector = this.selector;
        if (selector != null) {
            selector.firedFds.add(this);
            IOCPUtils.notify(selector.iocp);
        }
    }

    protected void clearReadable() {
        if (!firingReadable) {
            return;
        }
        firingReadable = false;
        if (!firingWritable) {
            clearEventsFromSelector();
        }
    }

    protected void clearWritable() {
        if (!firingWritable) {
            return;
        }
        firingWritable = false;
        if (!firingReadable) {
            clearEventsFromSelector();
        }
    }

    private void clearEventsFromSelector() {
        var selector = this.selector;
        if (selector != null) {
            selector.firedFds.remove(this);
        }
    }

    abstract protected void ioComplete(VIOContext ctx, int nbytes);

    abstract protected void ioError(VIOContext ctx, int ntstatus);
}
