package io.vproxy.vproxyx;

import io.vproxy.base.Config;
import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.connection.Connection;
import io.vproxy.base.connection.Connector;
import io.vproxy.base.connection.ServerSock;
import io.vproxy.base.http.connect.HttpConnectProtocolHandler;
import io.vproxy.base.processor.Hint;
import io.vproxy.base.protocol.ProtocolHandler;
import io.vproxy.base.protocol.ProtocolServerConfig;
import io.vproxy.base.protocol.ProtocolServerHandler;
import io.vproxy.base.util.*;
import io.vproxy.base.util.coll.Tuple4;
import io.vproxy.component.proxy.ConnectorGen;
import io.vproxy.component.proxy.Proxy;
import io.vproxy.component.proxy.ProxyNetConfig;
import io.vproxy.dns.DNSServer;
import io.vproxy.lib.http1.CoroutineHttp1Server;
import io.vproxy.msquic.MsQuicInitializer;
import io.vproxy.socks.Socks5ProxyProtocolHandler;
import io.vproxy.vfd.FDProvider;
import io.vproxy.vfd.FDs;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;
import io.vproxy.vproxyx.util.Browser;
import io.vproxy.vproxyx.websocks.*;
import io.vproxy.vproxyx.websocks.relay.DomainBinder;
import io.vproxy.vproxyx.websocks.relay.RelayBindAnyPortServer;
import io.vproxy.vproxyx.websocks.relay.RelayHttpServer;
import io.vproxy.vproxyx.websocks.relay.RelayHttpsServer;
import io.vproxy.vproxyx.websocks.ss.SSProtocolHandler;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@SuppressWarnings({"StringTemplateMigration"})
public class WebSocksProxyAgent {
    private static final String defaultConfigName = "vpws-agent.conf";

    public static void main0(String[] args) throws Exception {
        new WebSocksProxyAgent().launch(args);
    }

    private ConfigLoader configLoader = null;
    private ConfigProcessor configProcessor;
    private EventLoopGroup acceptor;
    private EventLoopGroup worker;

    private DNSServer dnsServer = null;
    private DNSServer dnsServer6 = null;
    private Proxy socks5 = null;
    private Proxy httpConnect = null;
    private Proxy ss = null;
    private ServerSock pacServer = null;
    private CoroutineHttp1Server relayHttp = null;
    private Proxy relayHttps = null;
    private Proxy relayAny = null;

    private void loadConfig(String[] args) throws Exception {
        if (configLoader != null) {
            return;
        }

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
                System.out.println("Please visit https://vproxy-tools.github.io/vpwsui/index.html to generate a config file");
                System.out.println("Or you may refer to the config file example https://github.com/wkgcass/vproxy/blob/master/doc/websocks-agent-example.conf");

                Browser.open("https://vproxy-tools.github.io/vpwsui/error-no-conf.html?configFile=" + URLEncoder.encode(configFile, StandardCharsets.UTF_8));
                Utils.exit(1);
                return;
            }
        } else {
            configFile = args[0];
        }
        configFile = Utils.filename(configFile);
        assert Logger.lowLevelDebug("config file path: " + configFile);

        configLoader = new ConfigLoader();
        configLoader.load(configFile);
    }

    public void launch() throws Exception {
        launch(new String[0]);
    }

    @SuppressWarnings("rawtypes")
    private void launch(String[] args) throws Exception {
        loadConfig(args);
        // get worker thread count
        int threads = Math.min(4, Runtime.getRuntime().availableProcessors());
        int workers = threads;
        if (threads > 3) {
            workers -= 1; // one core for acceptor if there are at least 4 processors
        }
        if (configLoader.isUdpOverTcpEnabled()) {
            workers = 1; // restrict to one thread
        }

        // initiate the acceptor event loop(s)
        acceptor = new EventLoopGroup("acceptor-group");
        acceptor.add("acceptor-loop");
        // initiate the worker event loop(s)
        // for quic, let the QuicRegistration init workers
        FDs quicFDs = null;
        if (configLoader.isQuicEnabled()) {
            if (!MsQuicInitializer.isSupported()) {
                throw new Exception("quic is not supported");
            }
            worker = new EventLoopGroup("worker-group", new Annotations(Map.of(
                AnnotationKeys.EventLoopGroup_UseMsQuic.name, "true"
            )));
            quicFDs = WebSocksQuicHelper.initClientQuic(worker, configLoader);
        } else {
            worker = new EventLoopGroup("worker-group");
            for (int i = 0; i < workers; ++i) {
                worker.add("worker-loop-" + i);
            }
        }

        // parse config
        configProcessor = new ConfigProcessor(configLoader, worker, worker);
        configProcessor.setQuicFDs(quicFDs);
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
            ).toList()
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
            WebSocksUtils.agentDNSServer = new AgentDNSServer("dns", new IPPort("0.0.0.0", port), worker, configProcessor, domainBinder);
            dnsServer = WebSocksUtils.agentDNSServer;
            // may need to start dns server
            if (configProcessor.getDnsListenPort() != 0) {
                assert Logger.lowLevelDebug("start dns server");
                WebSocksUtils.agentDNSServer.start();
                Logger.alert("dns server started on " + configProcessor.getDnsListenPort());

                if (!FDProvider.get().getProvided().isV4V6DualStack()) {
                    try {
                        dnsServer6 = new AgentDNSServer("dns6", new IPPort("::", port), worker, configProcessor, domainBinder);
                        dnsServer6.start();
                        Logger.alert("dns server for ipv6 started on " + configProcessor.getDnsListenPort());
                    } catch (Exception e) {
                        Logger.error(LogType.SYS_ERROR, "failed to launch dns on ipv6, skip and continue", e);
                        if (dnsServer6 != null) {
                            dnsServer6.stop();
                        }
                        dnsServer6 = null;
                    }
                }
            }
        }

        // initiate pool (it's inside the connector provider)
        WebSocksProxyAgentConnectorProvider connectorProvider = new WebSocksProxyAgentConnectorProvider(configProcessor);
        // initiate the agent
        // --------- port,   handler,       isGateway, postConstruct()
        List<Tuple4<Integer, ProtocolHandler, Boolean, Consumer<Proxy>>> handlers = new LinkedList<>();
        if (configProcessor.getSocks5ListenPort() != 0) {
            handlers.add(new Tuple4<>(
                configProcessor.getSocks5ListenPort(),
                new Socks5ProxyProtocolHandler(connectorProvider),
                configProcessor.isGateway(),
                proxy -> socks5 = proxy
            ));
        }
        if (configProcessor.getHttpConnectListenPort() != 0) {
            handlers.add(new Tuple4<>(
                configProcessor.getHttpConnectListenPort(),
                new HttpConnectProtocolHandler(connectorProvider),
                configProcessor.isGateway(),
                proxy -> httpConnect = proxy
            ));
        }
        if (configProcessor.getSsListenPort() != 0) {
            handlers.add(new Tuple4<>(
                configProcessor.getSsListenPort(),
                new SSProtocolHandler(configProcessor.getSsPassword(), connectorProvider),
                true,
                proxy -> ss = proxy
            ));
        }

        for (Tuple4<Integer, ProtocolHandler, Boolean, Consumer<Proxy>> tuple : handlers) {
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
                _ -> {
                    // close corresponding server
                    Logger.warn(LogType.ALERT, "closing server " + server.bind);
                    server.close();
                });

            // start the agent
            proxy.handle();

            tuple._4.accept(proxy);
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
            pacServer = lsn;
        }

        // maybe we can start the relay servers
        if (configProcessor.isDirectRelay()) {
            assert Logger.lowLevelDebug("start relay server");
            relayHttp = RelayHttpServer.launch(worker);
            Logger.alert("http relay server started on 80");
            relayHttps = new RelayHttpsServer(connectorProvider, configProcessor).launch(acceptor, worker);
            Logger.alert("https relay server started on 443");
            if (configProcessor.getDirectRelayIpRange() != null) {
                IPPort listen = configProcessor.getDirectRelayListen();
                Network ipRange = configProcessor.getDirectRelayIpRange();
                relayAny = new RelayBindAnyPortServer(connectorProvider, domainBinder, listen).launch(acceptor, worker);
                Logger.alert("relay-bind-any-port-server started on " + listen.formatToIPPortString() + " which handles " + ipRange);
            }
        }
    }

    @SuppressWarnings("DuplicatedCode")
    public void stop() {
        // stop all running instances
        if (dnsServer != null) {
            Logger.warn(LogType.ALERT, "stopping dnsServer: " + dnsServer.bindAddress);
            dnsServer.stop();
            dnsServer = null;
        }
        if (dnsServer6 != null) {
            Logger.warn(LogType.ALERT, "stopping dnsServer6: " + dnsServer6.bindAddress);
            dnsServer6.stop();
            dnsServer6 = null;
        }
        if (socks5 != null) {
            Logger.warn(LogType.ALERT, "stopping socks5: " + socks5.config.getServer().bind);
            socks5.stop();
            socks5 = null;
        }
        if (httpConnect != null) {
            Logger.warn(LogType.ALERT, "stopping httpConnect: " + httpConnect.config.getServer().bind);
            httpConnect.stop();
            httpConnect = null;
        }
        if (ss != null) {
            Logger.warn(LogType.ALERT, "stopping ss: " + ss.config.getServer().bind);
            ss.stop();
            ss = null;
        }
        if (pacServer != null) {
            Logger.warn(LogType.ALERT, "stopping pacServer: " + pacServer.bind);
            pacServer.close();
            pacServer = null;
        }
        if (relayHttp != null) {
            Logger.warn(LogType.ALERT, "stopping replayHttp: " + relayHttp);
            relayHttp.close();
            relayHttp = null;
        }
        if (relayHttps != null) {
            Logger.warn(LogType.ALERT, "stopping relayHttps: " + relayHttps.config.getServer().bind);
            relayHttps.stop();
            relayHttps = null;
        }
        if (relayAny != null) {
            Logger.warn(LogType.ALERT, "stopping relayAny: " + relayAny.config.getServer().bind);
            relayAny.stop();
            relayAny = null;
        }
        // release configProcessor
        // stop health check
        if (configProcessor != null) {
            var servers = configProcessor.getServers();
            for (var server : servers.values()) {
                Logger.warn(LogType.ALERT, "stopping server group: " + server.alias);
                server.clear();
            }
        }
        configProcessor = null;
        // terminate threads
        if (acceptor != null) {
            Logger.warn(LogType.ALERT, "terminating acceptor loops");
            acceptor.close();
            acceptor = null;
        }
        if (worker != null) {
            Logger.warn(LogType.ALERT, "terminating worker loops");
            worker.close();
            worker = null;
        }
        // release WebSocksUtils
        if (WebSocksUtils.agentDNSServer != null) {
            Logger.warn(LogType.ALERT, "stopping agentDNS");
            WebSocksUtils.agentDNSServer.stop();
            WebSocksUtils.agentDNSServer = null;
        }
        // clear config loader
        Logger.warn(LogType.ALERT, "clearing configLoader");
        configLoader = null;
    }

    public ConfigLoader getConfigLoader() {
        return configLoader;
    }

    public void setConfigLoader(ConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    public ConfigProcessor getConfigProcessor() {
        return configProcessor;
    }

    public DNSServer getDnsServer() {
        return dnsServer;
    }

    public DNSServer getDnsServer6() {
        return dnsServer6;
    }

    public Proxy getSocks5() {
        return socks5;
    }

    public Proxy getHttpConnect() {
        return httpConnect;
    }

    public Proxy getSs() {
        return ss;
    }

    public ServerSock getPacServer() {
        return pacServer;
    }

    public CoroutineHttp1Server getRelayHttp() {
        return relayHttp;
    }

    public Proxy getRelayHttps() {
        return relayHttps;
    }

    public Proxy getRelayAny() {
        return relayAny;
    }
}
