package vproxyx;

import vfd.IP;
import vfd.IPPort;
import vfd.VFDConfig;
import vproxy.component.proxy.ConnectorGen;
import vproxy.component.proxy.Proxy;
import vproxy.component.proxy.ProxyNetConfig;
import vproxy.socks.Socks5ProxyProtocolHandler;
import vproxybase.Config;
import vproxybase.component.elgroup.EventLoopGroup;
import vproxybase.connection.Connection;
import vproxybase.connection.Connector;
import vproxybase.connection.ServerSock;
import vproxybase.http.connect.HttpConnectProtocolHandler;
import vproxybase.processor.Hint;
import vproxybase.protocol.ProtocolHandler;
import vproxybase.protocol.ProtocolServerConfig;
import vproxybase.protocol.ProtocolServerHandler;
import vproxybase.util.Logger;
import vproxybase.util.Tuple3;
import vproxybase.util.Utils;
import vproxyx.util.Browser;
import vproxyx.websocks.*;
import vproxyx.websocks.relay.DomainBinder;
import vproxyx.websocks.relay.RelayBindAnyPortServer;
import vproxyx.websocks.relay.RelayHttpServer;
import vproxyx.websocks.relay.RelayHttpsServer;
import vproxyx.websocks.ss.SSProtocolHandler;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class WebSocksProxyAgent {
    private static final String defaultConfigName = "vpws-agent.conf";

    public static void main0(String[] args) throws Exception {
        String configFile = Config.workingDirectoryFile(defaultConfigName);
        if (args.length != 1 && args.length != 0) {
            System.out.println("You can only set config file path as the startup argument");
            System.out.println("If not specified, the config will be read from " + configFile);
            Utils.exit(1);
            return;
        }
        if (args.length == 0) {
            System.err.println("Warning: reading config from " + configFile);

            File file = new File(configFile);
            if (!file.exists()) {
                System.out.println("Config file not found at " + configFile);
                System.out.println("Please visit http://127.0.0.1:44380 to generate a config file");
                System.out.println("Or you may refer to the config file example https://github.com/wkgcass/vproxy/blob/master/doc/websocks-agent-example.conf");

                var adminServer = new AdminServer();
                adminServer.listen(44380);

                Browser.open("http://127.0.0.1:44380/error-no-conf.html?configFile=" + URLEncoder.encode(configFile, StandardCharsets.UTF_8));
                return;
            }
        } else {
            configFile = args[0];
        }
        configFile = Utils.filename(configFile);
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

        assert Logger.lowLevelDebug("socks5 listen on " + configProcessor.getSocks5ListenPort());
        assert Logger.lowLevelDebug("proxy domain patterns " + configProcessor.getDomains());
        assert Logger.lowLevelDebug("proxy resolve patterns " + configProcessor.getProxyResolves());
        assert Logger.lowLevelDebug("no-proxy domain patterns " + configProcessor.getNoProxyDomains());
        assert Logger.lowLevelDebug("https-sni-erasure domain patterns " + configProcessor.getHttpsSniErasureDomains());
        assert Logger.lowLevelDebug("proxy servers " +
            configProcessor.getServers().values().stream().map(server ->
                server.getServerHandles().stream()
                    .map(h -> "\n" + h)
            ).collect(Collectors.toList())
        );
        assert Logger.lowLevelDebug("cacerts file " + configProcessor.getCacertsPath());
        assert Logger.lowLevelDebug("cacerts password " + configProcessor.getCacertsPswd());

        // init the ssl context
        WebSocksUtils.initSslContext(configProcessor.getCacertsPath(), configProcessor.getCacertsPswd()
            , "JKS", false, configProcessor.isVerifyCert());
        if (!configProcessor.getHttpsSniErasureRelayCertKeys().isEmpty() || configProcessor.getAutoSignCert() != null) {
            WebSocksUtils.initHttpsSniErasureContext(configProcessor);
        }

        // domain binder
        DomainBinder domainBinder = null;
        if (configProcessor.getDirectRelayIpRange() != null) {
            domainBinder = new DomainBinder(worker.get("worker-loop-0").getSelectorEventLoop(), configProcessor.getDirectRelayIpRange());
        }

        // init dns server
        {
            int port = configProcessor.getDnsListenPort();
            if (port == 0) {
                port = 53; // just a hint. if not configured, the dns server won't start
            }
            var l4addr = new IPPort(IP.from("0.0.0.0"), port);
            WebSocksUtils.agentDNSServer = new AgentDNSServer("dns", l4addr, worker, configProcessor, domainBinder);
            // may need to start dns server
            if (configProcessor.getDnsListenPort() != 0) {
                assert Logger.lowLevelDebug("start dns server");
                WebSocksUtils.agentDNSServer.start();
                Logger.alert("dns server started on " + configProcessor.getDnsListenPort());
            }
        }

        // init admin server
        {
            int port = configProcessor.getAdminListenPort();
            if (port != 0) {
                var adminServer = new AdminServer();
                adminServer.listen(port);
            }
        }

        // initiate pool (it's inside the connector provider)
        WebSocksProxyAgentConnectorProvider connectorProvider = new WebSocksProxyAgentConnectorProvider(configProcessor);
        // initiate the agent
        // --------- port,   handler,         isGateway
        List<Tuple3<Integer, ProtocolHandler, Boolean>> handlers = new LinkedList<>();
        if (configProcessor.getSocks5ListenPort() != 0) {
            handlers.add(new Tuple3<>(
                configProcessor.getSocks5ListenPort(),
                new Socks5ProxyProtocolHandler(connectorProvider),
                configProcessor.isGateway()
            ));
        }
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
            var l4addr = new IPPort(IP.from(l3addr), port);
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
            var l4addr = new IPPort(
                IP.from("0.0.0.0"),
                configProcessor.getPacServerPort()
            );
            ServerSock.checkBind(l4addr);
            ServerSock lsn = ServerSock.create(l4addr);
            ProtocolServerHandler.apply(acceptor.next(), lsn,
                new ProtocolServerConfig().setInBufferSize(256).setOutBufferSize(256),
                new PACHandler(
                    configProcessor.getSocks5ListenPort(), // this port is socks5 port
                    configProcessor.getHttpConnectListenPort() // this port is http connect port
                ));
            Logger.alert("pac server started on " + configProcessor.getPacServerPort());
        }

        // maybe we can start the relay servers
        if (configProcessor.isDirectRelay()) {
            assert Logger.lowLevelDebug("start relay server");
            RelayHttpServer.launch(worker);
            Logger.alert("http relay server started on 80");
            new RelayHttpsServer(connectorProvider, configProcessor).launch(acceptor, worker);
            Logger.alert("https relay server started on 443");
            if (configProcessor.getDirectRelayIpRange() != null) {
                IPPort listen = configProcessor.getDirectRelayListen();
                String ipRange = configProcessor.getDirectRelayIpRange();
                new RelayBindAnyPortServer(connectorProvider, domainBinder, listen).launch(acceptor, worker);
                Logger.alert("relay-bind-any-port-server started on " + listen.formatToIPPortString() + " which handles " + ipRange);
            }
        }
    }
}
