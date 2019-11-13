package vproxy.selector.wrap.streamed;

import vfd.EventSet;
import vfd.ServerSocketFD;
import vfd.SocketFD;
import vproxy.selector.PeriodicEvent;
import vproxy.selector.SelectorEventLoop;
import vproxy.selector.wrap.arqudp.ArqUDPBasedFDs;
import vproxy.selector.wrap.arqudp.ArqUDPSocketFD;
import vproxy.selector.wrap.udp.UDPBasedFDs;
import vproxy.util.LogType;
import vproxy.util.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.function.Supplier;

public class StreamedArqUDPClientFDs implements UDPBasedFDs {
    private final ArqUDPBasedFDs fds;
    private final SelectorEventLoop loop;
    private final InetSocketAddress remote;

    private ArqUDPSocketFD fd;
    private boolean ready = false;
    private StreamedFDHandler currentHandler;
    private PeriodicEvent keepaliveEvent;
    private final Supplier<StreamedFDHandler> handlerSupplier;

    public StreamedArqUDPClientFDs(ArqUDPBasedFDs fds, SelectorEventLoop loop, InetSocketAddress remote,
                                   Supplier<StreamedFDHandler> handlerSupplier) throws IOException {
        this.fds = fds;
        this.loop = loop;
        this.remote = remote;
        this.handlerSupplier = handlerSupplier;

        init();
    }

    private void init() throws IOException {
        start();
    }

    private void stop() {
        Logger.error(LogType.PROBE, "streamed arq udp client is stopping: remote=" + remote);
        if (keepaliveEvent != null) {
            keepaliveEvent.cancel();
            keepaliveEvent = null;
        }
        ready = false;
        if (fd != null) {
            loop.remove(fd);
            try {
                fd.close();
            } catch (IOException e) {
                Logger.shouldNotHappen("closing fd " + fd + " failed", e);
            }
        }
        if (currentHandler != null) {
            currentHandler.clear();
            currentHandler = null;
        }
    }

    private void restart(ArqUDPSocketFD fd) {
        stop();
        try {
            start();
        } catch (IOException e) {
            Logger.shouldNotHappen("starting streamed arq udp client failed", e);
        }
    }

    private void start() throws IOException {
        Logger.probe("streamed arq udp client is starting: remote=" + remote);
        boolean failed = true;
        try {
            fd = fds.openSocketFD(loop);
            fd.connect(remote);
            currentHandler = handlerSupplier.get();
            currentHandler.init(fd, loop, this::ready, this::restart, null);
            loop.add(fd, EventSet.write(), null, currentHandler);
            failed = false;
        } finally {
            // release resources if failed
            if (failed) {
                if (fd != null) {
                    fd.close();
                }
            }
        }
    }

    private void ready(ArqUDPSocketFD fd) {
        ready = true;
        Logger.alert("streamed arq udp is ready: " + currentHandler.getClass().getSimpleName() + "(" + fd + ")");
        keepaliveEvent = loop.period(30_000, currentHandler::probe);
    }

    @Override
    public ServerSocketFD openServerSocketFD(SelectorEventLoop loop) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SocketFD openSocketFD(SelectorEventLoop loop) throws IOException {
        if (fd == null || !fd.isOpen()) {
            throw new IOException("not valid");
        }
        if (!ready) {
            throw new IOException("not ready");
        }
        return currentHandler.clientOpen();
    }
}
