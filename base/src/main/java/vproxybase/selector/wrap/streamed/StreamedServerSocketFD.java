package vproxybase.selector.wrap.streamed;

import vfd.FD;
import vfd.IPPort;
import vfd.ServerSocketFD;
import vfd.SocketFD;
import vproxybase.GlobalInspection;
import vproxybase.prometheus.GaugeF;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.selector.wrap.VirtualFD;
import vproxybase.selector.wrap.WrappedSelector;
import vproxybase.util.LogType;
import vproxybase.util.Logger;

import java.io.IOException;
import java.net.SocketOption;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;

public class StreamedServerSocketFD implements ServerSocketFD, VirtualFD {
    private static final String streamed_server_socket_fd_accept_queue_length_current = "streamed_server_socket_fd_accept_queue_length_current";

    static {
        GlobalInspection.getInstance().registerHelpMessage(streamed_server_socket_fd_accept_queue_length_current,
            "The current accept queue length of streamed fds from streamed server socket fd");
    }

    private final ServerSocketFD realFD;
    private final WrappedSelector selector;
    private final IPPort local;
    private final StreamedServerSocketFD[] serverPtr;

    private boolean isOpen = true;
    private final Deque<StreamedFD> acceptQueue = new LinkedList<>();

    private final GaugeF statisticsAcceptQueueLength;

    public StreamedServerSocketFD(ServerSocketFD realFD,
                                  SelectorEventLoop loop,
                                  IPPort local,
                                  StreamedServerSocketFD[] serverPtr) throws IOException {
        this.realFD = realFD;
        this.selector = loop.selector;
        this.local = local;
        this.serverPtr = serverPtr;

        synchronized (this.serverPtr) {
            if (this.serverPtr[0] != null) {
                throw new IOException("cannot create more than one streamed server socket fd");
            }
            this.serverPtr[0] = this;
        }

        statisticsAcceptQueueLength = GlobalInspection.getInstance().addMetric(streamed_server_socket_fd_accept_queue_length_current,
            Map.of("listen", local.formatToIPPortString()),
            (m, l) -> new GaugeF(m, l, () -> (long) acceptQueue.size()));
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
            cancelSelfFDReadable();
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
        assert Logger.lowLevelDebug("calling onRegister() on " + this);
        if (selfFDReadable) {
            setSelfFDReadable();
        }
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
        return realFD;
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

        GlobalInspection.getInstance().removeMetric(statisticsAcceptQueueLength);
    }

    void accepted(StreamedFD fd) {
        assert Logger.lowLevelDebug("accepted(" + fd + ") called on " + this);
        acceptQueue.add(fd);
        setSelfFDReadable();
    }

    private boolean selfFDReadable = false;

    private void setSelfFDReadable() {
        selfFDReadable = true;
        selector.registerVirtualReadable(this);
    }

    private void cancelSelfFDReadable() {
        selfFDReadable = false;
        selector.removeVirtualReadable(this);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "(local=" + local + ")";
    }
}
