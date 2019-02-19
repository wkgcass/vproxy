package net.cassite.vproxyx.websocks5;

import net.cassite.vproxy.component.check.CheckProtocol;
import net.cassite.vproxy.component.check.HealthCheckConfig;
import net.cassite.vproxy.component.elgroup.EventLoopGroup;
import net.cassite.vproxy.component.proxy.ConnectorGen;
import net.cassite.vproxy.component.proxy.Proxy;
import net.cassite.vproxy.component.proxy.ProxyNetConfig;
import net.cassite.vproxy.component.svrgroup.Method;
import net.cassite.vproxy.component.svrgroup.ServerGroup;
import net.cassite.vproxy.component.svrgroup.ServerGroups;
import net.cassite.vproxy.connection.BindServer;
import net.cassite.vproxy.connection.Connection;
import net.cassite.vproxy.connection.Connector;
import net.cassite.vproxy.protocol.ProtocolHandler;
import net.cassite.vproxy.socks.Socks5ProxyContext;
import net.cassite.vproxy.socks.Socks5ProxyProtocolHandler;
import net.cassite.vproxy.util.Callback;
import net.cassite.vproxy.util.Tuple;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

public class WebSocks5ProxyAgentMain {
    public static void main0(String[] args) throws Exception {
        // first of all, let's parse the configuration file
        // TODO --- below
        List<Pattern> proxyDomains = new LinkedList<>();
        // TODO --- upper

        // let's create a server, if bind failed, error would be thrown
        BindServer server = BindServer.create(
            new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), 1080 /*TODO*/));

        int threads = Math.min(4, Runtime.getRuntime().availableProcessors());
        int workers = threads;
        if (threads > 3) {
            workers -= 1; // one core for acceptor if there are at least 4 processors
        }
        // initiate the acceptor event loop(s)
        EventLoopGroup acceptor = new EventLoopGroup("acceptor-group");
        acceptor.add("acceptor-loop");
        // initiate the worker event loop(s)
        EventLoopGroup worker = new EventLoopGroup("worker-group");
        for (int i = 0; i < workers; ++i) {
            worker.add("worker-loop-" + i);
        }

        // create config for remote servers
        ServerGroups sgs = new ServerGroups("server-groups");
        ServerGroup servers = new ServerGroup("servers",
            worker, // use worker to run health check
            new HealthCheckConfig(1000, 30_000, 1, 2, CheckProtocol.tcp),
            Method.wrr);
        // TODO add server endpoints into the `servers` group
        servers.add("test",
            new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), 18080),
            InetAddress.getByAddress(new byte[]{127, 0, 0, 1}),
            10);
        servers.getServerHandles().forEach(h -> h.healthy = true); // set all servers to healthy

        // initiate the agent
        ProtocolHandler<Tuple<Socks5ProxyContext, Callback<Connector, IOException>>>
            handler =
            new Socks5ProxyProtocolHandler(
                new WebSocks5ProxyAgentConnectorProvider(proxyDomains, servers)
            );
        ConnectorGen<Socks5ProxyContext> connGen = new ConnectorGen<Socks5ProxyContext>() {
            @Override
            public Type type() {
                return Type.handler;
            }

            @Override
            public ProtocolHandler<Tuple<Socks5ProxyContext, Callback<Connector, IOException>>> handler() {
                return handler;
            }

            @Override
            public Connector genConnector(Connection accepted) {
                return null; // will not be called because type is `handler`
            }
        };
        Proxy proxy = new Proxy(
            new ProxyNetConfig()
                .setAcceptLoop(acceptor.next())
                .setInBufferSize(16384)
                .setOutBufferSize(16384)
                .setHandleLoopProvider(worker::next)
                .setServer(server)
                .setConnGen(() -> connGen),
            s -> {
                // do nothing, won't happen
                // when terminating, user should simply kill this process and won't close server
            });

        // start the agent
        proxy.handle();
    }
}
