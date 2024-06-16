package io.vproxy.base.selector.wrap;

import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.Logger;
import io.vproxy.vfd.FD;

import java.io.IOException;
import java.net.SocketOption;
import java.util.Objects;

public abstract class AbstractDelegateFD<SRC extends FD> implements VirtualFD, DelegatingSourceFD, DelegatingTargetFD {
    private SelectorEventLoop loop;
    private SRC sourceFD;
    private DelegatingTargetFD targetFD;
    private boolean closed = false;
    private IOException error;

    protected void setDelegatingSourceFD(SRC fd) {
        Objects.requireNonNull(fd);
        if (this.sourceFD != null)
            throw new IllegalStateException("sourceFD is already set");
        if (!(fd instanceof DelegatingSourceFD sfd)) {
            throw new IllegalArgumentException(fd + " is not DelegatingSourceFD");
        }

        assert Logger.lowLevelDebug(this + ".setDelegatingSourceFd(" + fd + ")");

        this.sourceFD = fd;
        sfd.setDelegatingTargetFD(this);

        if (loop != null) {
            sfd.loopAware(loop);
            if (isRegistered) {
                sfd.onRegister();
            }
        }
    }

    @Override
    public void setDelegatingTargetFD(DelegatingTargetFD fd) {
        Objects.requireNonNull(fd);
        if (targetFD != null)
            throw new IllegalStateException("targetFD is already set");

        assert Logger.lowLevelDebug(this + ".setDelegatingTargetFD(" + fd + ")");

        targetFD = fd;
        boolean readable = this.readable;
        boolean writable = this.writable;
        if (loop != null) {
            cancelReadable();
            cancelWritable();
            targetFD.loopAware(loop);
        }
        if (readable) {
            fd.setReadable();
        }
        if (writable) {
            fd.setWritable();
        }
    }

    protected void raiseError(IOException e) {
        if (closed) {
            return;
        }

        assert Logger.lowLevelDebug(this + ".raiseError(" + e + ")");

        error = e;
        if (loop == null) {
            setReadable();
            setWritable();
        } else {
            loop.runOnLoop(() -> {
                if (closed) {
                    return;
                }
                setReadable();
                setWritable();
            });
        }
    }

    protected void checkError() throws IOException {
        if (error != null)
            throw error;
    }

    protected SRC getSourceFD() {
        return sourceFD;
    }

    @Override
    public boolean loopAware(SelectorEventLoop loop) {
        if (this.loop != null && this.loop != loop) {
            return false;
        }
        if (sourceFD != null) {
            var res = sourceFD.loopAware(loop);
            if (!res) {
                return false;
            }
        }
        if (this.loop == null) {
            this.loop = loop;
            return true;
        }
        return true;
    }

    private boolean readable = false;
    private boolean writable = false;

    @Override
    public void setReadable() {
        assert Logger.lowLevelDebug(this + " setReadable");

        if (targetFD != null) {
            targetFD.setReadable();
            return;
        }
        readable = true;
        if (loop != null)
            loop.runOnLoop(() -> loop.selector.registerVirtualReadable(this));
    }

    @Override
    public void setWritable() {
        assert Logger.lowLevelDebug(this + " setWritable");

        if (targetFD != null) {
            targetFD.setWritable();
            return;
        }
        writable = true;
        if (loop != null)
            loop.runOnLoop(() -> loop.selector.registerVirtualWritable(this));
    }

    @Override
    public void cancelReadable() {
        assert Logger.lowLevelDebug(this + " cancelReadable");

        readable = false;
        if (targetFD != null) {
            targetFD.cancelReadable();
            return;
        }
        if (loop != null)
            loop.runOnLoop(() -> loop.selector.removeVirtualReadable(this));
    }

    @Override
    public void cancelWritable() {
        assert Logger.lowLevelDebug(this + " cancelWritable");

        writable = false;
        if (targetFD != null) {
            targetFD.cancelWritable();
            return;
        }
        if (loop != null)
            loop.runOnLoop(() -> loop.selector.removeVirtualWritable(this));
    }

    private boolean isRegistered = false;

    @Override
    public void onRegister() {
        if (sourceFD != null)
            ((VirtualFD) sourceFD).onRegister();
        isRegistered = true;

        if (targetFD == null) {
            if (readable)
                setReadable();
            if (writable)
                setWritable();
        }
    }

    @Override
    public void onRemove() {
        if (sourceFD != null)
            ((VirtualFD) sourceFD).onRemove();
        isRegistered = false;
        loop = null;
    }

    @Override
    public boolean isOpen() {
        if (sourceFD == null) {
            // the sourceFD might fail to init
            // the error must be delivered to the user
            // so by default isOpen() must return 'true'
            return true;
        }
        return sourceFD.isOpen();
    }

    @Override
    public void configureBlocking(boolean b) throws IOException {
        if (sourceFD == null) {
            if (b) {
                throw new IOException("blocking mode is not supported");
            }
            return;
        }
        sourceFD.configureBlocking(b);
    }

    @Override
    public <T> void setOption(SocketOption<T> name, T value) throws IOException {
        if (sourceFD == null)
            return;
        sourceFD.setOption(name, value);
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (sourceFD == null)
            return;
        sourceFD.close();
    }

    @Override
    public FD real() {
        return sourceFD;
    }

    @Override
    public boolean contains(FD fd) {
        return this == fd || (this.sourceFD != null && (this.sourceFD == fd || this.sourceFD.contains(fd)));
    }
}
