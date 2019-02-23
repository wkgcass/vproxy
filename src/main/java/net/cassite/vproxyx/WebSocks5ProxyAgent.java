package net.cassite.vproxyx;

import net.cassite.vproxy.app.cmd.handle.resource.ServerHandle;
import net.cassite.vproxy.component.check.CheckProtocol;
import net.cassite.vproxy.component.check.HealthCheckConfig;
import net.cassite.vproxy.component.elgroup.EventLoopGroup;
import net.cassite.vproxy.component.proxy.ConnectorGen;
import net.cassite.vproxy.component.proxy.Proxy;
import net.cassite.vproxy.component.proxy.ProxyNetConfig;
import net.cassite.vproxy.component.svrgroup.Method;
import net.cassite.vproxy.component.svrgroup.ServerGroup;
import net.cassite.vproxy.connection.BindServer;
import net.cassite.vproxy.connection.Connection;
import net.cassite.vproxy.connection.Connector;
import net.cassite.vproxy.protocol.ProtocolHandler;
import net.cassite.vproxy.socks.Socks5ProxyContext;
import net.cassite.vproxy.socks.Socks5ProxyProtocolHandler;
import net.cassite.vproxy.util.Callback;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.Tuple;
import net.cassite.vproxyx.websocks5.ConfigProcessor;
import net.cassite.vproxyx.websocks5.WebSocks5ProxyAgentConnectorProvider;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class WebSocks5ProxyAgent {
    public static void main0(String[] args) throws Exception {
        // debug option
        //noinspection ConstantConditions,TrivialFunctionalExpressionUsage
        assert ((Predicate<Void>) v -> {
            System.setProperty("javax.net.debug", "all");
            return true;
        }).test(null);

        String configFile = "~/vproxy-websocks5-agent.conf";
        if (args.length != 1 && args.length != 0) {
            System.out.println("You can only set config file path as the startup argument");
            System.out.println("If not specified, the config will be read from " + configFile);
            System.exit(1);
            return;
        }
        if (args.length == 0) {
            System.err.println("Warning: reading config from " + configFile);
        } else {
            configFile = args[0];
        }
        if (configFile.startsWith("~")) {
            configFile = System.getProperty("user.home") + configFile.substring(1);
        }
        assert Logger.lowLevelDebug("config file path: " + configFile);

        // get worker thread count
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
        ServerGroup servers = new ServerGroup("servers",
            worker, // use worker to run health check
            new HealthCheckConfig(1000, 30_000, 1, 2, CheckProtocol.tcp),
            Method.wrr);

        // parse config
        ConfigProcessor configProcessor = new ConfigProcessor(configFile, servers);
        configProcessor.parse();

        assert Logger.lowLevelDebug("listen on " + configProcessor.getListenPort());
        assert Logger.lowLevelDebug("proxy domain patterns " + configProcessor.getDomains());
        assert Logger.lowLevelDebug("proxy servers " +
            servers.getServerHandles().stream().map(h -> "\n" + new ServerHandle.ServerRef(h)).collect(Collectors.toList()));

        // let's create a server, if bind failed, error would be thrown
        BindServer server = BindServer.create(
            new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), configProcessor.getListenPort()));

        // initiate the agent
        ProtocolHandler<Tuple<Socks5ProxyContext, Callback<Connector, IOException>>>
            handler =
            new Socks5ProxyProtocolHandler(
                new WebSocks5ProxyAgentConnectorProvider(
                    configProcessor.getDomains(),
                    servers,
                    configProcessor.getUser(),
                    configProcessor.getPass())
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
                .setOutBufferSize(65536)
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
