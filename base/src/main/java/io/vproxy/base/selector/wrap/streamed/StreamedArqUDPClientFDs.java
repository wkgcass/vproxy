package io.vproxy.base.selector.wrap.streamed;

import io.vproxy.base.Config;
import io.vproxy.base.selector.PeriodicEvent;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.selector.wrap.arqudp.ArqUDPBasedFDs;
import io.vproxy.base.selector.wrap.arqudp.ArqUDPSocketFD;
import io.vproxy.base.selector.wrap.udp.UDPBasedFDs;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.vfd.EventSet;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.ServerSocketFD;
import io.vproxy.vfd.SocketFD;

import java.io.IOException;
import java.util.function.Supplier;

public class StreamedArqUDPClientFDs implements UDPBasedFDs {
    private final ArqUDPBasedFDs fds;
    private final SelectorEventLoop loop;
    private final IPPort remote;

    private ArqUDPSocketFD fd;
    private boolean ready = false;
    private StreamedFDHandler currentHandler;
    private PeriodicEvent keepaliveEvent;
    private final Supplier<StreamedFDHandler> handlerSupplier;

    public StreamedArqUDPClientFDs(ArqUDPBasedFDs fds, SelectorEventLoop loop, IPPort remote,
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
        Logger.error(LogType.ALERT, "streamed arq udp client is stopping: remote=" + remote);
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
            // restart in 2 seconds
            loop.delay(2_000, () -> restart(fd));
        }
    }

    private void start() throws IOException {
        if (Config.probe.contains("streamed-arq-udp-event")) {
            Logger.probe("streamed arq udp client is starting: remote=" + remote);
        }
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
                    fd = null;
                }
            }
        }
    }

    private void ready(ArqUDPSocketFD fd) {
        ready = true;
        Logger.alert("streamed arq udp is ready: " + currentHandler.getClass().getSimpleName() + "(" + fd + ")");
        keepaliveEvent = loop.period(10_000, currentHandler::keepalive);
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
