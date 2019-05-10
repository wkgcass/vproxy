package net.cassite.vproxy.component.app;

import net.cassite.vproxy.component.elgroup.EventLoopGroup;
import net.cassite.vproxy.component.elgroup.EventLoopGroupAttach;
import net.cassite.vproxy.component.elgroup.EventLoopWrapper;
import net.cassite.vproxy.component.exception.AlreadyExistException;
import net.cassite.vproxy.component.exception.ClosedException;
import net.cassite.vproxy.component.exception.NotFoundException;
import net.cassite.vproxy.component.proxy.*;
import net.cassite.vproxy.component.secure.SecurityGroup;
import net.cassite.vproxy.component.svrgroup.ServerGroups;
import net.cassite.vproxy.connection.*;
import net.cassite.vproxy.processor.Processor;
import net.cassite.vproxy.processor.ProcessorProvider;
import net.cassite.vproxy.util.LogType;
import net.cassite.vproxy.util.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TcpLB {
    class LBProxyEventHandler implements ProxyEventHandler {
        @Override
        public void serverRemoved(BindServer server) {
            // it's removed, so close the listening fd
            server.close();
            servers.remove(server);

            Logger.info(LogType.ALERT, "server " + server + " is removed from the acceptor group, " +
                servers.size() + " server(s) left");
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
                start(); // we call start(). how to start new servers will be determined in start() method
            } catch (IOException e) {
                Logger.shouldNotHappen("the proxy start failed " + e);
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
    private int timeout; // modifiable
    private int inBufferSize; // modifiable
    private int outBufferSize; // modifiable
    public final String protocol;
    public final Processor processor;
    public SecurityGroup securityGroup;
    // the modifiable fields only have effect when new connection arrives

    // true means the lb is stopped, but it can still re-start.
    // false means we WANT the lb to start,
    // whether it's actually started, see proxy
    private boolean stopped = true;
    // true means the lb is fully teared down, server port is closed, and cannot be restored
    // false means the opposite
    private boolean destroyed = false;

    private final LBAttach attach;

    public final ConcurrentMap<BindServer, Proxy> servers = new ConcurrentHashMap<>();
    private final LBProxyEventHandler proxyEventHandler = new LBProxyEventHandler();

    public TcpLB(String alias,
                 EventLoopGroup acceptorGroup,
                 EventLoopGroup workerGroup,
                 InetSocketAddress bindAddress,
                 ServerGroups backends,
                 int timeout,
                 int inBufferSize, int outBufferSize,
                 SecurityGroup securityGroup) throws AlreadyExistException, ClosedException {
        this(alias, acceptorGroup, workerGroup, bindAddress, backends, timeout, inBufferSize, outBufferSize, "tcp", securityGroup);
    }

    public TcpLB(String alias,
                 EventLoopGroup acceptorGroup,
                 EventLoopGroup workerGroup,
                 InetSocketAddress bindAddress,
                 ServerGroups backends,
                 int timeout,
                 int inBufferSize, int outBufferSize,
                 String protocol,
                 SecurityGroup securityGroup) throws AlreadyExistException, ClosedException {
        this.alias = alias;
        this.acceptorGroup = acceptorGroup;
        this.workerGroup = workerGroup;
        this.bindAddress = bindAddress;
        this.backends = backends;
        this.timeout = timeout;
        this.inBufferSize = inBufferSize;
        this.outBufferSize = outBufferSize;
        this.protocol = protocol;
        this.processor = (protocol.equals("tcp") ? null : ProcessorProvider.getInstance().get(protocol));
        this.securityGroup = securityGroup;

        // we do not bind or create proxy object here
        // if it's created, it should start to run
        // so create it in start() method

        // attach to acceptorGroup
        this.attach = new LBAttach();
        acceptorGroup.attachResource(attach);
    }

    // this method can override
    protected ConnectorGen provideConnectorGen() {
        if (protocol.equals("tcp")) {
            return this::connectorProvider;
        } else {
            return new ConnectorGen() {
                @Override
                public Type type() {
                    return Type.processor;
                }

                @Override
                public Connector genConnector(Connection accepted) {
                    return connectorProvider(accepted);
                }

                @Override
                public Processor processor() {
                    return processor;
                }
            };
        }
    }

    // provide a connector
    private Connector connectorProvider(Connection clientConn) {
        // check whitelist
        InetAddress remoteAddress = clientConn.remote.getAddress();
        if (!securityGroup.allow(Protocol.TCP, remoteAddress, bindAddress.getPort()))
            return null; // terminated by securityGroup

        // we get a new connector

        // get a server from backends
        Connector connector = backends.next(clientConn.remote);
        if (connector == null)
            return null; // return null if cannot get any
        assert Logger.lowLevelDebug("got a backend: " + connector);
        return connector;
    }

    private ProxyNetConfig getProxyNetConfig(BindServer server, NetEventLoop eventLoop) {
        return new ProxyNetConfig()
            .setConnGen(provideConnectorGen())
            .setHandleLoopProvider(() -> {
                // get a event loop from group
                EventLoopWrapper w = workerGroup.next();
                if (w == null)
                    return null; // return null if cannot get any
                assert Logger.lowLevelDebug("use event loop: " + w.alias);
                return w;
            })
            .setTimeout(timeout)
            .setInBufferSize(inBufferSize)
            .setOutBufferSize(outBufferSize)
            .setServer(server)
            .setAcceptLoop(eventLoop);
    }

    public void start() throws IOException {
        assert Logger.lowLevelDebug("start() called on lb " + alias);
        synchronized (this) {
            if (destroyed) {
                throw new IOException("the lb is already destroyed");
            }

            stopped = false;

            List<EventLoopWrapper> eventLoops = acceptorGroup.list();
            if (eventLoops.isEmpty()) {
                assert Logger.lowLevelDebug("cannot start because event loop list is empty, will start later");
                return;
            }

            // if a loop is already bond, we should not re-bind it to a server
            // so we first extract bond loops and check later
            Set<NetEventLoop> alreadyBondLoops = new HashSet<>();
            for (Proxy pxy : servers.values()) {
                alreadyBondLoops.add(pxy.config.getAcceptLoop());
            }

            for (EventLoopWrapper w : eventLoops) {
                if (alreadyBondLoops.contains(w))
                    continue; // ignore already bond loops

                // start one server for each new event loop
                BindServer server = BindServer.create(this.bindAddress);
                ProxyNetConfig proxyNetConfig = getProxyNetConfig(server, w);
                Proxy proxy = new Proxy(proxyNetConfig, proxyEventHandler);

                try {
                    proxy.handle();
                } catch (IOException e) {
                    assert Logger.lowLevelDebug("calling proxy.handle() failed");
                    server.close();
                    throw e;
                }

                servers.put(server, proxy);
                Logger.info(LogType.ALERT, "server " + bindAddress + " starts on loop: " + w.alias);
            }

            assert Logger.lowLevelDebug("lb " + alias + " started");
        }
    }

    public void stop() {
        assert Logger.lowLevelDebug("stop() called on lb " + alias);
        stopped = true;

        synchronized (this) {
            for (Proxy pxy : new HashSet<>(servers.values())/*here we use a new hash set, to make sure we only remove the existing proxies*/) {
                pxy.stop(); // when it's stopped, the listening server will be closed in the serverRemoved callback
            }
            servers.clear();
        }
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
    }

    public int sessionCount() {
        int cnt = 0;
        for (Proxy pxy : servers.values()) {
            cnt += pxy.sessionCount();
        }
        return cnt;
    }

    public void copySessions(Collection<? super Session> coll) {
        for (Proxy pxy : servers.values()) {
            pxy.copySessions(coll);
        }
    }

    public void setInBufferSize(int inBufferSize) {
        this.inBufferSize = inBufferSize;
        for (Proxy pxy : servers.values()) {
            pxy.config.setInBufferSize(inBufferSize);
        }
    }

    public void setOutBufferSize(int outBufferSize) {
        this.outBufferSize = outBufferSize;
        for (Proxy pxy : servers.values()) {
            pxy.config.setOutBufferSize(inBufferSize);
        }
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
        for (Proxy pxy : servers.values()) {
            pxy.config.setTimeout(inBufferSize);
        }
    }

    public int getInBufferSize() {
        return inBufferSize;
    }

    public int getOutBufferSize() {
        return outBufferSize;
    }

    public int getTimeout() {
        return timeout;
    }
}
