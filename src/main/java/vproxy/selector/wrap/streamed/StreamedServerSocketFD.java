package vproxy.selector.wrap.streamed;

import vfd.FD;
import vfd.IPPort;
import vfd.ServerSocketFD;
import vfd.SocketFD;
import vproxy.selector.SelectorEventLoop;
import vproxy.selector.wrap.VirtualFD;
import vproxy.selector.wrap.WrappedSelector;
import vproxy.util.LogType;
import vproxy.util.Logger;

import java.io.IOException;
import java.net.SocketOption;
import java.util.Deque;
import java.util.LinkedList;

public class StreamedServerSocketFD implements ServerSocketFD, VirtualFD {
    private final ServerSocketFD readFD;
    private final WrappedSelector selector;
    private final IPPort local;
    private final StreamedServerSocketFD[] serverPtr;

    private boolean isOpen = true;
    private final Deque<StreamedFD> acceptQueue = new LinkedList<>();

    public StreamedServerSocketFD(ServerSocketFD readFD,
                                  SelectorEventLoop loop,
                                  IPPort local,
                                  StreamedServerSocketFD[] serverPtr) throws IOException {
        this.readFD = readFD;
        this.selector = loop.selector;
        this.local = local;
        this.serverPtr = serverPtr;

        synchronized (this.serverPtr) {
            if (this.serverPtr[0] != null) {
                throw new IOException("cannot create more than one streamed server socket fd");
            }
            this.serverPtr[0] = this;
        }
    }

    @Override
    public IPPort getLocalAddress() {
        return local;
    }

    @Override
    public SocketFD accept() throws IOException {
        assert Logger.lowLevelDebug("accept() called on " + this);
        if (!isOpen) {
            throw new IOException("the fd is closed: " + this);
        }
        StreamedFD fd = acceptQueue.poll();
        if (fd == null) {
            selector.removeVirtualReadable(this);
        }
        return fd;
    }

    @Override
    public void bind(IPPort l4addr) throws IOException {
        if (!l4addr.equals(local)) {
            throw new IOException("cannot bind " + l4addr + "(you could only bind " + local + ")");
        }
    }

    @Override
    public void onRegister() {
        // ignore
        assert Logger.lowLevelDebug("calling onRegister() on " + this);
    }

    @Override
    public void onRemove() {
        Logger.error(LogType.IMPROPER_USE, "removing the streamed server socket fd from loop: " + this);
    }

    @Override
    public void configureBlocking(boolean b) {
        // ignore
    }

    @Override
    public <T> void setOption(SocketOption<T> name, T value) {
        // ignore
    }

    @Override
    public FD real() {
        return readFD;
    }

    @Override
    public boolean isOpen() {
        return isOpen;
    }

    @Override
    public void close() {
        isOpen = false;
        synchronized (this.serverPtr) {
            this.serverPtr[0] = null;
        }
    }

    void accepted(StreamedFD fd) {
        assert Logger.lowLevelDebug("accepted(" + fd + ") called on " + this);
        acceptQueue.add(fd);
        selector.registerVirtualReadable(this);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "(local=" + local + ")";
    }
}
