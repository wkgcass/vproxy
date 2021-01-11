package vserver.impl;

import vfd.IPPort;
import vproxybase.Config;
import vproxybase.connection.NetEventLoop;
import vproxybase.connection.ServerSock;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.util.Logger;
import vproxybase.util.thread.VProxyThread;
import vserver.GeneralServer;

import java.io.IOException;

public abstract class AbstractServer implements GeneralServer {
    protected boolean closed = false;
    protected NetEventLoop loop;
    private final boolean noInputLoop;
    private ServerSock server;

    public AbstractServer() {
        this(null);
    }

    public AbstractServer(NetEventLoop loop) {
        this.loop = loop;
        noInputLoop = loop == null;
    }

    @Override
    public void listen(IPPort addr) throws IOException {
        if (Config.checkBind) {
            ServerSock.checkBind(addr);
        }

        initLoop();

        server = ServerSock.create(addr);
        listen(server);
    }

    protected abstract String threadname();

    private void initLoop() throws IOException {
        if (loop != null) {
            return;
        }
        loop = new NetEventLoop(SelectorEventLoop.open());
        loop.getSelectorEventLoop().loop(r -> VProxyThread.create(r, threadname()));
    }

    abstract public void listen(ServerSock server) throws IOException;

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        if (noInputLoop) {
            // should stop the event loop because it's created from inside
            if (loop != null) {
                try {
                    loop.getSelectorEventLoop().close();
                } catch (IOException e) {
                    Logger.shouldNotHappen("got error when closing the event loop", e);
                }
            }
        }
        if (server != null) {
            server.close();
        }
    }
}
