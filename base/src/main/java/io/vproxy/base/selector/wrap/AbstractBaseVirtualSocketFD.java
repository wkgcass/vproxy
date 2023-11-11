package io.vproxy.base.selector.wrap;

import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.Logger;
import io.vproxy.vfd.FD;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.SocketFD;
import io.vproxy.vfd.abs.AbstractBaseFD;

import java.io.IOException;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.util.Objects;

public abstract class AbstractBaseVirtualSocketFD extends AbstractBaseFD implements SocketFD, VirtualFD, DelegatingSourceFD {
    private SelectorEventLoop loop;
    protected final boolean isAccepted;

    private boolean closed = false;
    private boolean connected;
    private boolean resetWhenClosing;

    private boolean connectCalled = false;
    private boolean connectedEventFires = false;

    private IPPort remote;
    private IPPort local;

    private boolean shutdownOutputIsCalled;
    private boolean eof;
    private IOException error;

    public AbstractBaseVirtualSocketFD(boolean isAccepted, IPPort local, IPPort remote) {
        this.isAccepted = isAccepted;
        this.connected = isAccepted;
        this.local = local;
        this.remote = remote;
    }

    protected void setRemote(IPPort remote) {
        this.remote = remote;
    }

    protected void setLocal(IPPort local) {
        this.local = local;
    }

    protected void checkOpen() throws IOException {
        if (closed) throw new IOException("closed");
    }

    protected void checkConnected() throws IOException {
        if (!connected) throw new IOException("not connected yet");
    }

    protected void checkError() throws IOException {
        if (error != null) throw error;
    }

    protected SelectorEventLoop getLoop() {
        return loop;
    }

    @Override
    public boolean loopAware(SelectorEventLoop loop) {
        if (this.loop == null) {
            this.loop = loop;
            return true;
        }
        // check whether loops are the same
        return this.loop == loop;
    }

    @Override
    public void connect(IPPort l4addr) throws IOException {
        checkOpen();
        if (connected) throw new IOException("already connected");
        if (connectCalled) throw new IOException("connect() already called");
        remote = l4addr;
        connectCalled = true;
    }

    @Override
    public boolean isConnected() {
        return !closed && connected;
    }

    @Override
    public void shutdownOutput() throws IOException {
        checkOpen();
        checkConnected();
        shutdownOutputIsCalled = true;
    }

    public boolean isShutdownOutput() {
        return shutdownOutputIsCalled;
    }

    @Override
    public boolean finishConnect() throws IOException {
        checkOpen();
        if (connected) throw new IOException("already connected");
        if (!connectCalled) throw new IOException("not trying to connect");
        checkError();
        if (connectedEventFires) {
            connected = true;
            return true;
        } else {
            return false;
        }
    }

    protected void alertConnected(IPPort local) {
        assert Logger.lowLevelDebug("alert connected: " + this + ", local=" + local);
        this.local = local;
        this.connectedEventFires = true;
        setWritable();
    }

    @Override
    public IPPort getLocalAddress() throws IOException {
        checkOpen();
        return local;
    }

    @Override
    public IPPort getRemoteAddress() throws IOException {
        checkOpen();
        return remote;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        checkOpen();
        checkConnected();
        checkError();
        if (eof) {
            if (noDataToRead()) {
                return -1;
            }
        }
        if (dst.limit() == dst.position()) {
            return 0; // empty dst
        }
        if (noDataToRead()) {
            cancelReadable();
            return 0;
        }
        int ret = doRead(dst);
        if (noDataToRead()) {
            cancelReadable();
        }
        return ret;
    }

    protected abstract boolean noDataToRead();

    protected abstract int doRead(ByteBuffer dst) throws IOException;

    protected void setEof() {
        this.eof = true;
        setReadable();
    }

    protected boolean isEof() {
        return eof;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        checkOpen();
        checkConnected();
        checkError();
        int ret = doWrite(src);
        if (noSpaceToWrite()) {
            cancelWritable();
        }
        return ret;
    }

    protected abstract boolean noSpaceToWrite();

    protected abstract int doWrite(ByteBuffer src) throws IOException;

    protected void raiseError(Throwable err) {
        if (closed) {
            assert Logger.lowLevelDebug("got new error, but the fd is already closed, error " + err + " will not raise");
            return;
        }
        assert Logger.lowLevelDebug("got new error: " + err);
        if (err instanceof IOException) {
            error = (IOException) err;
        } else {
            error = new IOException(err);
        }
        if (loop == null) {
            setReadable();
            setWritable();
        } else {
            loop.runOnLoop(() -> {
                if (closed) {
                    assert Logger.lowLevelDebug("raising error " + err + " canceled, because the fd is already closed");
                    return;
                }
                setReadable();
                setWritable();
            });
        }
    }

    @Override
    public void configureBlocking(boolean b) {
        if (b) throw new UnsupportedOperationException();
    }

    @Override
    public <T> void setOption(SocketOption<T> name, T value) {
        // only SO_LINGER supported, but do not raise error for other options
        if (name == StandardSocketOptions.SO_LINGER) {
            resetWhenClosing = Integer.valueOf(0).equals(value);
        }
    }

    @Override
    public FD real() {
        return null;
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public void close() { // virtual fd must not raise exceptions when closing
        if (closed) {
            superClose();
            return;
        }
        assert Logger.lowLevelDebug(this + ".close() called, reset=" + resetWhenClosing);
        closeSelf();
        doClose(resetWhenClosing);
    }

    private void closeSelf() {
        superClose();
        closed = true;
    }

    private void superClose() {
        try {
            super.close();
        } catch (IOException e) {
            Logger.shouldNotHappen("closing base fd failed", e);
            throw new RuntimeException(e);
        }
    }

    protected abstract void doClose(boolean reset);

    @Override
    public String toString() {
        return formatToString();
    }

    protected abstract String formatToString();

    // ======
    // events
    // ======

    private DelegatingTargetFD delegatingTargetFD;

    @Override
    public void setDelegatingTargetFD(DelegatingTargetFD fd) {
        Objects.requireNonNull(fd);
        if (delegatingTargetFD != null)
            throw new IllegalStateException("delegatingTargetFD is already set");
        delegatingTargetFD = fd;
    }

    private boolean readable = false;

    protected void setReadable() {
        readable = true;
        if (loop == null) {
            return;
        }
        if (delegatingTargetFD != null) {
            delegatingTargetFD.setReadable();
            return;
        }
        loop.runOnLoop(() -> loop.selector.registerVirtualReadable(this));
    }

    protected void cancelReadable() {
        readable = false;
        if (loop == null) {
            return;
        }
        if (delegatingTargetFD != null) {
            delegatingTargetFD.cancelReadable();
            return;
        }
        loop.runOnLoop(() -> loop.selector.removeVirtualReadable(this));
    }

    private boolean writable = false;

    protected void setWritable() {
        writable = true;
        if (loop == null) {
            return;
        }
        if (delegatingTargetFD != null) {
            delegatingTargetFD.setWritable();
            return;
        }
        loop.runOnLoop(() -> loop.selector.registerVirtualWritable(this));
    }

    protected void cancelWritable() {
        writable = false;
        if (loop == null) {
            return;
        }
        if (delegatingTargetFD != null) {
            delegatingTargetFD.cancelWritable();
            return;
        }
        loop.runOnLoop(() -> loop.selector.removeVirtualWritable(this));
    }

    @Override
    public void onRegister() {
        if (readable) {
            setReadable();
        }
        if (writable) {
            setWritable();
        }
    }

    @Override
    public void onRemove() {
        // do nothing
    }
}
