package vproxyx;

import vfd.VFDConfig;
import vproxy.app.cmd.handle.resource.ServerHandle;
import vproxy.component.elgroup.EventLoopGroup;
import vproxy.component.proxy.ConnectorGen;
import vproxy.component.proxy.Proxy;
import vproxy.component.proxy.ProxyNetConfig;
import vproxy.connection.Connection;
import vproxy.connection.Connector;
import vproxy.connection.ServerSock;
import vproxy.http.connect.HttpConnectProtocolHandler;
import vproxy.processor.Hint;
import vproxy.protocol.ProtocolHandler;
import vproxy.protocol.ProtocolServerConfig;
import vproxy.protocol.ProtocolServerHandler;
import vproxy.socks.Socks5ProxyProtocolHandler;
import vproxy.util.Logger;
import vproxy.util.Tuple3;
import vproxy.util.Utils;
import vproxyx.websocks.*;
import vproxyx.websocks.ss.SSProtocolHandler;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class WebSocksProxyAgent {
    private static final String defaultConfigName = "vproxy-websocks-agent.conf";

    public static void main0(String[] args) throws Exception {
        String configFile = System.getProperty("user.home") + File.separator + defaultConfigName;
        if (args.length != 1 && args.length != 0) {
            System.out.println("You can only set config file path as the startup argument");
            System.out.println("If not specified, the config will be read from " + configFile);
            System.exit(1);
            return;
        }
        if (args.length == 0) {
            System.err.println("Warning: reading config from " + configFile);

            File file = new File(configFile);
            if (!file.exists()) {
                ConfigGenerator.interactive(System.getProperty("user.home"), defaultConfigName);
            }
        } else {
            configFile = args[0];
        }
        if (configFile.startsWith("~")) {
            configFile = System.getProperty("user.home") + configFile.substring(1);
        }
        assert Logger.lowLevelDebug("config file path: " + configFile);

        // get worker thread count
        int threads = Math.min(4, Runtime.getRuntime().availableProcessors());
        if (VFDConfig.useFStack) {
            threads = 1;
        }
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

        // parse config
        ConfigProcessor configProcessor = new ConfigProcessor(configFile, worker, worker);
        configProcessor.parse();

        assert Logger.lowLevelDebug("listen on " + configProcessor.getListenPort());
        assert Logger.lowLevelDebug("proxy domain patterns " + configProcessor.getDomains());
        assert Logger.lowLevelDebug("proxy resolve patterns " + configProcessor.getResolves());
        assert Logger.lowLevelDebug("no-proxy domain patterns " + configProcessor.getNoProxyDomains());
        assert Logger.lowLevelDebug("tls-relay domain patterns " + configProcessor.getTLSRelayDomains());
        assert Logger.lowLevelDebug("proxy servers " +
            configProcessor.getServers().values().stream().map(server ->
                server.getServerHandles().stream()
                    .map(h -> "\n" + new ServerHandle.ServerRef(h)).collect(Collectors.toList())
            ).collect(Collectors.toList())
        );
        assert Logger.lowLevelDebug("cacerts file " + configProcessor.getCacertsPath());
        assert Logger.lowLevelDebug("cacerts password " + configProcessor.getCacertsPswd());

        // init the ssl context
        WebSocksUtils.initSslContext(configProcessor.getCacertsPath(), configProcessor.getCacertsPswd()
            , "JKS", false, configProcessor.isVerifyCert());
        if (!configProcessor.getTLSRelayCertKeys().isEmpty()) {
            WebSocksUtils.initTLSRelayContext(configProcessor.getTLSRelayCertKeys());
        }

        // initiate pool (it's inside the connector provider)
        WebSocksProxyAgentConnectorProvider connectorProvider = new WebSocksProxyAgentConnectorProvider(configProcessor);
        // initiate the agent
        // --------- port,   handler,         isGateway
        List<Tuple3<Integer, ProtocolHandler, Boolean>> handlers = new LinkedList<>();
        handlers.add(new Tuple3<>(
            configProcessor.getListenPort(),
            new Socks5ProxyProtocolHandler(connectorProvider),
            configProcessor.isGateway()
        ));
        if (configProcessor.getHttpConnectListenPort() != 0) {
            handlers.add(new Tuple3<>(
                configProcessor.getHttpConnectListenPort(),
                new HttpConnectProtocolHandler(connectorProvider),
                configProcessor.isGateway()
            ));
        }
        if (configProcessor.getSsListenPort() != 0) {
            handlers.add(new Tuple3<>(
                configProcessor.getSsListenPort(),
                new SSProtocolHandler(configProcessor.getSsPassword(), connectorProvider),
                true
            ));
        }

        for (Tuple3<Integer, ProtocolHandler, Boolean> tuple : handlers) {
            int port = tuple._1;
            ProtocolHandler handler = tuple._2;
            ConnectorGen connGen = new ConnectorGen() {
                @Override
                public Type type() {
                    return Type.handler;
                }

                @Override
                public ProtocolHandler handler() {
                    return handler;
                }

                @Override
                public Connector genConnector(Connection accepted, Hint hint) {
                    return null; // will not be called because type is `handler`
                }
            };

            // let's create a server, if bind failed, error would be thrown
            var l3addr = tuple._3
                ? "0.0.0.0"
                : "127.0.0.1";
            var l4addr = new InetSocketAddress(Utils.l3addr(l3addr), port);
            ServerSock.checkBind(l4addr);
            ServerSock server = ServerSock.create(l4addr);

            Proxy proxy = new Proxy(
                new ProxyNetConfig()
                    .setAcceptLoop(acceptor.next())
                    .setInBufferSize(24576)
                    .setOutBufferSize(24576)
                    .setHandleLoopProvider(worker::next)
                    .setServer(server)
                    .setConnGen(connGen),
                s -> {
                    // do nothing, won't happen
                    // when terminating, user should simply kill this process and won't close server
                });

            // start the agent
            proxy.handle();

            Logger.alert("agent started on " + l3addr + ":" + port + " " + tuple._2.getClass().getSimpleName());
        }

        // maybe we can start the pac server
        if (configProcessor.getPacServerPort() != 0) {
            assert Logger.lowLevelDebug("start pac server");
            var l4addr = new InetSocketAddress(
                Utils.l3addr("0.0.0.0"),
                configProcessor.getPacServerPort()
            );
            ServerSock.checkBind(l4addr);
            ServerSock lsn = ServerSock.create(l4addr);
            ProtocolServerHandler.apply(acceptor.next(), lsn,
                new ProtocolServerConfig().setInBufferSize(256).setOutBufferSize(256),
                new PACHandler(
                    configProcessor.getPacServerIp(),
                    configProcessor.getListenPort(), // this port is socks5 port
                    configProcessor.getHttpConnectListenPort() // this port is http connect port
                ));
            Logger.alert("pac server started on " + configProcessor.getPacServerPort());
        }

        // maybe we can start the dns server
        if (configProcessor.getDnsListenPort() != 0) {
            assert Logger.lowLevelDebug("start dns server");
            var l4addr = new InetSocketAddress(
                Utils.l3addr("0.0.0.0"),
                configProcessor.getDnsListenPort()
            );
            HttpDNSServer httpDNSServer = new HttpDNSServer("dns", l4addr, worker, configProcessor);
            httpDNSServer.start();
            Logger.alert("dns server started on " + configProcessor.getDnsListenPort());
            WebSocksUtils.httpDNSServer = httpDNSServer;
        }

        // maybe we can start the relay servers
        if (configProcessor.isDirectRelay()) {
            assert Logger.lowLevelDebug("start relay server");
            RelayHttpServer.launch(worker);
            Logger.alert("http relay server started on 80");
            RelayHttpsServer.launch(acceptor, worker);
            Logger.alert("https relay server started on 443");
        }
    }
}
