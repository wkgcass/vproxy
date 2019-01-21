package net.cassite.vproxy.component.app;

import net.cassite.vproxy.component.elgroup.EventLoopGroup;
import net.cassite.vproxy.component.elgroup.EventLoopGroupAttach;
import net.cassite.vproxy.component.elgroup.EventLoopWrapper;
import net.cassite.vproxy.component.exception.AlreadyExistException;
import net.cassite.vproxy.component.exception.ClosedException;
import net.cassite.vproxy.component.proxy.Proxy;
import net.cassite.vproxy.component.proxy.ProxyEventHandler;
import net.cassite.vproxy.component.proxy.ProxyNetConfig;
import net.cassite.vproxy.component.svrgroup.Connector;
import net.cassite.vproxy.component.svrgroup.ServerGroups;
import net.cassite.vproxy.connection.BindServer;
import net.cassite.vproxy.selector.SelectorEventLoop;
import net.cassite.vproxy.util.LogType;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.Tuple;

import java.io.IOException;
import java.net.InetSocketAddress;

public class LB {
    class LBProxyEventHandler implements ProxyEventHandler {
        @Override
        public void serverRemoved(BindServer server) {
            if (stopped) {
                assert Logger.lowLevelDebug("the proxy server removed, " +
                    "but we do not create a new one because lb is stopped");
                return;
            }
            assert Logger.lowLevelDebug("bindServer removed from loop, maybe the loop is closed. " +
                "but lb(" + alias + ") is started, let's re-start or re-dispatch it");
            proxyNetConfig.setAcceptLoop(null); // remove the loop from config, it will be assigned in start()
            try {
                if (proxy == null)
                    start(); // proxy not exist, should run start
                else
                    redispatch(); // proxy exists, should run re-dispatch (assign the server channel to another selector)
            } catch (IOException e) {
                Logger.fatal(LogType.UNEXPECTED, "the proxy start failed " + e);
            }
        }
    }

    class LBAttach implements EventLoopGroupAttach {
        @Override
        public String id() {
            return "LB:" + alias;
        }

        @Override
        public void onEventLoopAdd() {
            if (stopped)
                return; // ignore when lb is stopped
            try {
                start(); // we call start(). whether already started will be determined in start() method
            } catch (IOException e) {
                Logger.fatal(LogType.UNEXPECTED, "the proxy start failed " + e);
            }
        }

        @Override
        public void onClose() {
            destroy(); // the event loop group is closed, we should destroy the lb
        }
    }

    public final String alias;
    public final EventLoopGroup acceptorGroup;
    public final EventLoopGroup workerGroup;
    public final InetSocketAddress bindAddress;
    public final ServerGroups backends;

    // true means the lb is stopped, but it can still re-start.
    // false means we WANT the lb to start,
    // whether it's actually started, see proxy
    private boolean stopped = true;
    // true means the lb is fully teared down, server port is closed, and cannot be restored
    // false means the opposite
    private boolean destroyed = false;
    // null means it's not actually started, however we MAY want it to start
    // we will try our best to make it start
    private Proxy proxy = null;

    private final BindServer server;
    private final ProxyNetConfig proxyNetConfig = new ProxyNetConfig();
    private final LBProxyEventHandler proxyEventHandler = new LBProxyEventHandler();

    public LB(String alias,
              EventLoopGroup acceptorGroup,
              EventLoopGroup workerGroup,
              InetSocketAddress bindAddress,
              ServerGroups backends,
              int inBufferSize,
              int outBufferSize) throws IOException, AlreadyExistException, ClosedException {
        this.alias = alias;
        this.acceptorGroup = acceptorGroup;
        this.workerGroup = workerGroup;
        this.bindAddress = bindAddress;
        this.backends = backends;

        // create server
        this.server = BindServer.create(bindAddress);

        // init proxyNetConfig
        // acceptEventLoop will be assigned in start() method
        this.proxyNetConfig
            .setConnGen(clientConn -> {
                // get a server from backends
                Connector connector = backends.next();
                if (connector == null)
                    return null; // return null if cannot get any
                assert Logger.lowLevelDebug("got a backend: " + connector);
                return connector;
            })
            .setHandleLoopProvider(() -> {
                // get a event loop from group
                Tuple<EventLoopWrapper, SelectorEventLoop> tuple = workerGroup.next();
                if (tuple == null)
                    return null; // return null if cannot get any
                assert Logger.lowLevelDebug("use event loop: " + tuple.left.alias);
                return tuple.left;
            })
            .setInBufferSize(inBufferSize)
            .setOutBufferSize(outBufferSize)
            .setServer(this.server);
        // we do not create proxy object here
        // if it's created, it should start to run
        // so create it in start() method

        // attach to acceptorGroup
        acceptorGroup.attachResource(new LBAttach());
    }

    // the proxy still exist
    // but we should dispatch server to another event loop
    private void redispatch() throws IOException {
        assert Logger.lowLevelDebug("redispatch() called on lb " + alias);
        Tuple<EventLoopWrapper, SelectorEventLoop> tuple = acceptorGroup.next();
        if (tuple == null) {
            // event loop tuple not returned
            // we should remove the proxy object
            proxy = null;
            Logger.warn(LogType.NO_EVENT_LOOP, "cannot get event loop when trying to re-dispatch the proxy server");
            return;
        }
        assert Logger.lowLevelDebug("got a event loop, do re-dispatch");
        proxyNetConfig.setAcceptLoop(tuple.left);
        proxy.handle();
    }

    public void start() throws IOException {
        assert Logger.lowLevelDebug("start() called on lb " + alias);
        synchronized (this) {
            if (proxy != null) { // quick handle when proxy is not null
                assert Logger.lowLevelDebug("already started, ignore the start() call");
                stopped = false;
                return;
            }

            if (destroyed) {
                throw new IOException("the lb is already destroyed");
            }

            stopped = false;

            Tuple<EventLoopWrapper, SelectorEventLoop> tuple = acceptorGroup.next();
            if (tuple == null) {
                assert Logger.lowLevelDebug("cannot start because event loop not retrieved, will start later");
                return;
            }
            proxyNetConfig.setAcceptLoop(tuple.left);

            proxy = new Proxy(proxyNetConfig, proxyEventHandler);

            try {
                proxy.handle();
            } catch (IOException e) {
                assert Logger.lowLevelDebug("calling proxy.handle() failed");
                this.proxy = null; // remove the proxy object if start failed
                proxyNetConfig.setAcceptLoop(null); // remove the loop from config
                throw e;
            }

            assert Logger.lowLevelDebug("lb " + alias + " started");
        }
    }

    public void stop() {
        assert Logger.lowLevelDebug("stop() called on lb " + alias);
        stopped = true;
        Proxy proxy;
        synchronized (this) {
            proxy = this.proxy;
            if (proxy == null)
                return; // already stopped
            this.proxy = null;
        }
        proxy.stop();
        this.proxyNetConfig.setAcceptLoop(null); // remove the event loop from config
    }

    public void destroy() {
        assert Logger.lowLevelDebug("destroy() called on lb " + alias);
        synchronized (this) {
            stop();
            if (destroyed)
                return;
            destroyed = true;
        }
        server.close();
    }
}
