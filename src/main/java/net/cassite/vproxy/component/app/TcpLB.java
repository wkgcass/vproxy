package net.cassite.vproxy.component.app;

import net.cassite.vproxy.component.elgroup.EventLoopGroup;
import net.cassite.vproxy.component.elgroup.EventLoopGroupAttach;
import net.cassite.vproxy.component.elgroup.EventLoopWrapper;
import net.cassite.vproxy.component.exception.AlreadyExistException;
import net.cassite.vproxy.component.exception.ClosedException;
import net.cassite.vproxy.component.exception.NotFoundException;
import net.cassite.vproxy.component.proxy.Proxy;
import net.cassite.vproxy.component.proxy.ProxyEventHandler;
import net.cassite.vproxy.component.proxy.ProxyNetConfig;
import net.cassite.vproxy.component.proxy.Session;
import net.cassite.vproxy.component.secure.SecurityGroup;
import net.cassite.vproxy.component.svrgroup.ServerGroups;
import net.cassite.vproxy.connection.BindServer;
import net.cassite.vproxy.connection.Connection;
import net.cassite.vproxy.connection.Connector;
import net.cassite.vproxy.connection.Protocol;
import net.cassite.vproxy.selector.SelectorEventLoop;
import net.cassite.vproxy.selector.TimerEvent;
import net.cassite.vproxy.util.LogType;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.ThreadSafe;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TcpLB {
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
                Logger.shouldNotHappen("the proxy start failed " + e);
            }
        }
    }

    class LBAttach implements EventLoopGroupAttach {
        @Override
        public String id() {
            return "TcpLB:" + alias;
        }

        @Override
        public void onEventLoopAdd() {
            if (stopped)
                return; // ignore when lb is stopped
            try {
                start(); // we call start(). whether already started will be determined in start() method
            } catch (IOException e) {
                Logger.shouldNotHappen("the proxy start failed " + e);
            }
        }

        @Override
        public void onClose() {
            destroy(); // the event loop group is closed, we should destroy the lb
        }
    }

    @ThreadSafe(false)
    public class Persist {
        public final InetAddress clientAddress;
        public final Connector connector;
        TimerEvent timeoutEvent;

        Persist(InetAddress clientAddress, Connector connector) {
            this.clientAddress = clientAddress;
            this.connector = connector;

            refresh();
        }

        void refresh() {
            // stop the old timer first
            if (timeoutEvent != null) {
                timeoutEvent.cancel();
                timeoutEvent = null;
            }
            // always handle the timeout on accept event loop
            // then there will be no concurrency
            SelectorEventLoop loop = proxyNetConfig.getAcceptLoop().getSelectorEventLoop();
            if (loop == null) {
                // cannot handle the persist timeout
                // so let's just remove the persist entry from map
                persistMap.remove(clientAddress);
                return;
            }
            timeoutEvent = loop.delay(persistTimeout, () ->
                /* persistence expired */ persistMap.remove(clientAddress));
        }

        public void remove() {
            if (timeoutEvent != null) {
                timeoutEvent.cancel();
                timeoutEvent = null;
            }
            // remove from map
            persistMap.remove(clientAddress);
        }
    }

    public final String alias;
    public final EventLoopGroup acceptorGroup;
    public final EventLoopGroup workerGroup;
    public final InetSocketAddress bindAddress;
    public final ServerGroups backends;
    private int inBufferSize; // modifiable
    private int outBufferSize; // modifiable
    public final SecurityGroup securityGroup;
    public int persistTimeout; // modifiable
    // the modifiable fields only have effect when new connection arrives

    // the persisted connector map
    // it will only be modified from one thread
    // in data panel, no need to handle concurrency
    // though it might be retrieved from control panel
    // so we use concurrent hash map instead
    public final ConcurrentMap<InetAddress, Persist> persistMap = new ConcurrentHashMap<>();

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

    private final LBAttach attach;

    public final BindServer server;
    private final ProxyNetConfig proxyNetConfig = new ProxyNetConfig();
    private final LBProxyEventHandler proxyEventHandler = new LBProxyEventHandler();

    public TcpLB(String alias,
                 EventLoopGroup acceptorGroup,
                 EventLoopGroup workerGroup,
                 InetSocketAddress bindAddress,
                 ServerGroups backends,
                 int inBufferSize, int outBufferSize,
                 SecurityGroup securityGroup,
                 int persistTimeout) throws IOException, AlreadyExistException, ClosedException {
        this.alias = alias;
        this.acceptorGroup = acceptorGroup;
        this.workerGroup = workerGroup;
        this.bindAddress = bindAddress;
        this.backends = backends;
        this.inBufferSize = inBufferSize;
        this.outBufferSize = outBufferSize;
        this.securityGroup = securityGroup;
        this.persistTimeout = persistTimeout;

        // create server
        this.server = BindServer.create(bindAddress);

        // init proxyNetConfig
        // acceptEventLoop will be assigned in start() method
        this.proxyNetConfig
            .setConnGen(this::connectorProvider) // the handle code is too long for a lambda, so move into a method
            .setHandleLoopProvider(() -> {
                // get a event loop from group
                EventLoopWrapper w = workerGroup.next();
                if (w == null)
                    return null; // return null if cannot get any
                assert Logger.lowLevelDebug("use event loop: " + w.alias);
                return w;
            })
            .setInBufferSize(inBufferSize)
            .setOutBufferSize(outBufferSize)
            .setServer(this.server);
        // we do not create proxy object here
        // if it's created, it should start to run
        // so create it in start() method

        // attach to acceptorGroup
        this.attach = new LBAttach();
        try {
            acceptorGroup.attachResource(attach);
        } catch (AlreadyExistException e) {
            this.server.close(); // close the socket if attach failed
            throw e;
        }
    }

    // provide a connector
    private Connector connectorProvider(Connection clientConn) {
        // check whitelist
        InetAddress remoteAddress = clientConn.remote.getAddress();
        if (!securityGroup.allow(Protocol.TCP, remoteAddress, bindAddress.getPort()))
            return null; // terminated by securityGroup
        // check persist
        Persist persist = persistMap.get(remoteAddress);
        if (persist != null) {
            if (persistTimeout == 0) {
                persist.remove();
            } else {
                if (persist.connector.isValid()) {
                    if (persistTimeout != 0)
                        persist.refresh();
                    return persist.connector;
                } else {
                    // the backend is not valid now
                    // remove the persist record
                    persist.remove();
                }
            }
        }
        // then we get a new connector

        // get a server from backends
        Connector connector = backends.next();
        if (connector == null)
            return null; // return null if cannot get any
        assert Logger.lowLevelDebug("got a backend: " + connector);
        // record the connector
        if (persistTimeout > 0) {
            Persist p = new Persist(remoteAddress, connector);
            persistMap.put(remoteAddress, p);
        }
        return connector;
    }

    // the proxy still exist
    // but we should dispatch server to another event loop
    private void redispatch() throws IOException {
        assert Logger.lowLevelDebug("redispatch() called on lb " + alias);
        EventLoopWrapper w = acceptorGroup.next();
        if (w == null) {
            // event loop not returned
            // we should remove the proxy object
            proxy = null;
            Logger.warn(LogType.NO_EVENT_LOOP, "cannot get event loop when trying to re-dispatch the proxy server");
            return;
        }
        assert Logger.lowLevelDebug("got a event loop, do re-dispatch");
        proxyNetConfig.setAcceptLoop(w);

        // also, we should re-dispatch the persist records
        // before start accepting connections
        // to ensure no race condition
        for (Persist p : persistMap.values()) {
            p.refresh(); // NOTE: the timeout is reset
        }

        // start accepting connections
        // before re-dispatch servers
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

            EventLoopWrapper w = acceptorGroup.next();
            if (w == null) {
                assert Logger.lowLevelDebug("cannot start because event loop not retrieved, will start later");
                return;
            }
            proxyNetConfig.setAcceptLoop(w);

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

        try {
            acceptorGroup.detachResource(attach);
        } catch (NotFoundException e) {
            // ignore
        }
        server.close();
    }

    public int sessionCount() {
        Proxy p = proxy;
        if (p == null) {
            return 0;
        }
        return p.sessionCount();
    }

    public void copySessions(Collection<? super Session> coll) {
        Proxy p = proxy;
        if (p == null) {
            return;
        }
        p.copySessions(coll);
    }

    public void setInBufferSize(int inBufferSize) {
        this.inBufferSize = inBufferSize;
        proxyNetConfig.setInBufferSize(inBufferSize);
    }

    public void setOutBufferSize(int outBufferSize) {
        this.outBufferSize = outBufferSize;
        proxyNetConfig.setOutBufferSize(outBufferSize);
    }

    public int getInBufferSize() {
        return inBufferSize;
    }

    public int getOutBufferSize() {
        return outBufferSize;
    }
}
