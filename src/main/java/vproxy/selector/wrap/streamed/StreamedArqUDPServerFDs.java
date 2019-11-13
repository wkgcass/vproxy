package vproxy.selector.wrap.streamed;

import vfd.EventSet;
import vfd.ServerSocketFD;
import vfd.SocketFD;
import vproxy.selector.Handler;
import vproxy.selector.HandlerContext;
import vproxy.selector.PeriodicEvent;
import vproxy.selector.SelectorEventLoop;
import vproxy.selector.wrap.arqudp.ArqUDPBasedFDs;
import vproxy.selector.wrap.arqudp.ArqUDPServerSocketFD;
import vproxy.selector.wrap.arqudp.ArqUDPSocketFD;
import vproxy.selector.wrap.udp.UDPBasedFDs;
import vproxy.util.LogType;
import vproxy.util.Logger;
import vproxy.util.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.function.Supplier;

public class StreamedArqUDPServerFDs implements UDPBasedFDs {
    private final ArqUDPBasedFDs fds;
    private final SelectorEventLoop loop;
    private final InetSocketAddress local;

    private ArqUDPServerSocketFD fd;
    private Map<ArqUDPSocketFD, PeriodicEvent> keepaliveEvents = new HashMap<>();
    private Map<ArqUDPSocketFD, StreamedFDHandler> currentHandlers = new HashMap<>();
    private Supplier<StreamedFDHandler> handlerSupplier;

    private final StreamedServerSocketFD[] serverPtr = new StreamedServerSocketFD[1];

    protected StreamedArqUDPServerFDs(ArqUDPBasedFDs fds, SelectorEventLoop loop, InetSocketAddress local,
                                      Supplier<StreamedFDHandler> handlerSupplier) throws IOException {
        this.fds = fds;
        this.loop = loop;
        this.local = local;
        this.handlerSupplier = handlerSupplier;

        init();
    }

    private void init() throws IOException {
        initProbe();
        start();
    }

    private void initProbe() {
        loop.period(30_000, () -> {
            String localStr = Utils.l4addrStr(local);
            for (Map.Entry<ArqUDPSocketFD, StreamedFDHandler> entry : currentHandlers.entrySet()) {
                ArqUDPSocketFD k = entry.getKey();
                // StreamedFDHandler v = entry.getValue();

                try {
                    String remoteStr = Utils.l4addrStr((InetSocketAddress) k.getRemoteAddress());
                    Logger.probe("accepted: " + localStr + " <- " + remoteStr);
                } catch (Throwable t) {
                    Logger.shouldNotHappen("got exception when probing", t);
                }
            }
        });
    }

    private void stop() {
        for (PeriodicEvent keepaliveEvent : keepaliveEvents.values()) {
            keepaliveEvent.cancel();
        }
        keepaliveEvents.clear();
        if (fd != null) {
            loop.remove(fd);
            try {
                fd.close();
            } catch (IOException e) {
                Logger.shouldNotHappen("closing fd " + fd + " failed", e);
            }
        }
        for (StreamedFDHandler currentHandler : currentHandlers.values()) {
            currentHandler.clear();
        }
        currentHandlers.clear();
    }

    private void restart() {
        stop();
        try {
            start();
        } catch (IOException e) {
            Logger.shouldNotHappen("starting streamed arq udp client failed", e);
        }
    }

    private void start() throws IOException {
        boolean failed = true;
        try {
            fd = fds.openServerSocketFD(loop);
            fd.bind(local);
            loop.add(fd, EventSet.read(), null, new Handler<>() {
                @Override
                public void accept(HandlerContext<ArqUDPServerSocketFD> ctx) {
                    while (true) {
                        ArqUDPSocketFD accepted;
                        try {
                            accepted = ctx.getChannel().accept();
                        } catch (IOException e) {
                            Logger.error(LogType.CONN_ERROR, "accepting fd " + fd + " failed", e);
                            return;
                        }
                        if (accepted == null) {
                            // ignore if no sockets
                            return;
                        }
                        StreamedFDHandler handler = handlerSupplier.get();
                        handler.init(accepted, loop, this::ready, this::invalid, this::accepted);
                        try {
                            loop.add(accepted, EventSet.read(), null, handler);
                        } catch (IOException e) {
                            Logger.error(LogType.EVENT_LOOP_ADD_FAIL, "adding fd " + accepted + " to event loop failed", e);
                            try {
                                accepted.close();
                            } catch (IOException ex) {
                                Logger.error(LogType.CONN_ERROR, "closing fd " + accepted + " failed", ex);
                            }
                        }
                        currentHandlers.put(accepted, handler);
                    }
                }

                private void ready(ArqUDPSocketFD fd) {
                    StreamedFDHandler h = currentHandlers.get(fd);
                    assert h != null;
                    loop.period(30_000, h::probe);
                    Logger.alert("streamed arq udp is ready: " + h.getClass().getSimpleName() + "(" + fd + ")");
                }

                private void invalid(ArqUDPSocketFD fd) {
                    loop.remove(fd);
                    try {
                        fd.close();
                    } catch (IOException e) {
                        Logger.error(LogType.CONN_ERROR, "closing fd " + fd + " failed", e);
                    }
                    StreamedFDHandler h = currentHandlers.get(fd);
                    if (h != null) {
                        h.clear();
                    }
                    PeriodicEvent e = keepaliveEvents.get(fd);
                    if (e != null) {
                        e.cancel();
                    }
                }

                private boolean accepted(StreamedFD fd) {
                    StreamedServerSocketFD server = null;
                    synchronized (serverPtr) {
                        if (serverPtr[0] != null) {
                            server = serverPtr[0];
                        }
                    }
                    if (server == null) {
                        return false;
                    } else {
                        server.accepted(fd);
                        return true;
                    }
                }

                @Override
                public void connected(HandlerContext<ArqUDPServerSocketFD> ctx) {
                    Logger.shouldNotHappen("connected should not fire");
                }

                @Override
                public void readable(HandlerContext<ArqUDPServerSocketFD> ctx) {
                    Logger.shouldNotHappen("readable should not fire");
                }

                @Override
                public void writable(HandlerContext<ArqUDPServerSocketFD> ctx) {
                    Logger.shouldNotHappen("writable should not fire");
                }

                @Override
                public void removed(HandlerContext<ArqUDPServerSocketFD> ctx) {
                    Logger.error(LogType.IMPROPER_USE, "the streamed arq udp server fd " + fd + " is removed from loop");
                    restart();
                }
            });
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

    @Override
    public ServerSocketFD openServerSocketFD(SelectorEventLoop loop) throws IOException {
        if (fd == null || !fd.isOpen()) {
            throw new IOException("not valid");
        }
        return new StreamedServerSocketFD(fd, loop, local, serverPtr);
    }

    @Override
    public SocketFD openSocketFD(SelectorEventLoop loop) {
        throw new UnsupportedOperationException();
    }
}
