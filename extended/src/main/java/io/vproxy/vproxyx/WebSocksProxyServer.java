package io.vproxy.vproxyx;

import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.connection.*;
import io.vproxy.vproxyx.websocks.*;
import io.vproxy.vproxyx.websocks.uot.UdpOverTcpSetup;
import io.vproxy.base.processor.Hint;
import io.vproxy.base.protocol.ProtocolHandler;
import io.vproxy.base.protocol.ProtocolServerConfig;
import io.vproxy.base.protocol.ProtocolServerHandler;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.selector.wrap.VirtualFD;
import io.vproxy.base.selector.wrap.h2streamed.H2StreamedServerFDs;
import io.vproxy.base.selector.wrap.kcp.KCPFDs;
import io.vproxy.base.selector.wrap.udp.UDPFDs;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.callback.Callback;
import vproxy.base.util.coll.Tuple;
import io.vproxy.component.proxy.ConnectorGen;
import io.vproxy.component.proxy.NetEventLoopProvider;
import io.vproxy.component.proxy.Proxy;
import io.vproxy.component.proxy.ProxyNetConfig;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.VFDConfig;
import vproxyx.websocks.*;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public class WebSocksProxyServer {
    public static void main0(String[] args) throws Exception {
        Map<String, String> auth = new HashMap<>();
        int port = -1;
        boolean ssl = false;
        String pkcs12 = null;
        String pkcs12pswd = null;
        String certpem = null;
        String keypem = null;
        String domain = null;
        int redirectPort = -1;
        boolean useKcp = false;
        int udpOverTcpPort = -1;
        String udpOverTcpNic = "eth0";
        String webroot = null;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            String next = i == args.length - 1 ? null : args[i + 1];
            //noinspection IfCanBeSwitch
            if (arg.equals("listen")) {
                if (next == null) {
                    throw new IllegalArgumentException("`listen` should be followed with a port argument");
                }
                try {
                    port = Integer.parseInt(next);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("port is not a number");
                }
                if (port < 1 || port > 65535) {
                    throw new IllegalArgumentException("port is not valid");
                }
                ++i;
            } else if (arg.equals("auth")) {
                if (next == null) {
                    throw new IllegalArgumentException("`auth` should be followed with a sequence of `user:password`");
                }
                String[] pairs = next.split(",");
                for (String pair : pairs) {
                    String[] userpass = pair.split(":");
                    if (userpass.length != 2 || userpass[0].trim().isEmpty() || userpass[1].trim().isEmpty())
                        throw new IllegalArgumentException("invalid user:password pair: " + pair);
                    auth.put(userpass[0].trim(), userpass[1].trim());
                }
                ++i;
            } else if (arg.equals("ssl")) {
                ssl = true;
            } else if (arg.equals("pkcs12")) {
                if (next == null) {
                    throw new IllegalArgumentException("`pkcs12` should be followed with a pkcs12 file");
                }
                pkcs12 = next;
                ++i;
            } else if (arg.equals("pkcs12pswd")) {
                if (next == null) {
                    throw new IllegalArgumentException("`pkcs12pswd` should be followed with the password of pkcs12 file");
                }
                pkcs12pswd = next;
                ++i;
            } else if (arg.equals("certpem")) {
                if (next == null) {
                    throw new IllegalArgumentException("`certpem` should be followed with the cert file path, separated with `,`");
                }
                certpem = next;
                ++i;
            } else if (arg.equals("keypem")) {
                if (next == null) {
                    throw new IllegalArgumentException("`keypem` should be followed with the key file path");
                }
                keypem = next;
                ++i;
            } else if (arg.equals("domain")) {
                if (next == null) {
                    throw new IllegalArgumentException("`domain` should be followed with the domain name");
                }
                domain = next;
                ++i;
            } else if (arg.equals("redirectport")) {
                if (next == null) {
                    throw new IllegalArgumentException("`redirectport` should be followed with a port argument");
                }
                try {
                    redirectPort = Integer.parseInt(next);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("redirectport is not a number");
                }
                if (redirectPort < 1 || redirectPort > 65535) {
                    throw new IllegalArgumentException("redirectport is not valid");
                }
                ++i;
            } else if (arg.equals("kcp")) {
                useKcp = true;
            } else if (arg.equals("uot.port")) {
                if (next == null) {
                    throw new IllegalArgumentException("`uot.port` should be followed with a port number");
                }
                try {
                    udpOverTcpPort = Integer.parseInt(next);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("uot.port is not a number");
                }
                if (udpOverTcpPort < 1 || udpOverTcpPort > 65535) {
                    throw new IllegalArgumentException("uot.port is not valid");
                }
                ++i;
            } else if (arg.equals("uot.nic")) {
                if (next == null) {
                    throw new IllegalArgumentException("`uot.nic` should be followed with nic to bind");
                }
                udpOverTcpNic = next;
                ++i;
            } else if (arg.equals("webroot")) {
                if (next == null) {
                    throw new IllegalArgumentException("`webroot` should be followed with a directory path");
                }
                if (!Files.isDirectory(Path.of(next))) {
                    throw new IllegalArgumentException("webroot is not a directory");
                }
                webroot = next;
                ++i;
            } else
                throw new IllegalArgumentException("unknown argument: " + arg + ".\n" +
                    "argument: listen {} auth {} \\\n" +
                    "          [ssl (pkcs12 {} pkcs12pswd {})|(certpem {} keypem {})] [domain {}] \\\n" +
                    "          [redirectport {}] [kcp [uot.port {} [uot.nic {eth0}]]] \\\n" +
                    "          [webroot {}] \n" +
                    "examples: listen 443 auth alice:pasSw0rD ssl pkcs12 ~/my.p12 pkcs12pswd paSsWorD domain example.com redirectport 80\n" +
                    "          listen 443 auth alice:pasSw0rD ssl \\\n" +
                    "                  certpem /etc/letsencrypt/live/example.com/cert.pem,/etc/letsencrypt/live/example.com/chain.pem \\\n" +
                    "                  keypem /etc/letsencrypt/live/example.com/privkey.pem \\\n" +
                    "                  domain example.com redirectport 80\n" +
                    " [no ssl] listen 80 auth alice:pasSw0rD,bob:pAssW0Rd");
        }
        if (port == -1)
            throw new IllegalArgumentException("listening port is not specified. `listen $PORT`, " +
                "example: listen 443");
        if (auth.isEmpty())
            throw new IllegalArgumentException("authentication not specified. `auth $USER:$PASS[,$USER2:$PASS2[,...]]`, " +
                "example: auth alice:pasSw0rD,bob:PaSsw0Rd");
        if (ssl) {
            if (pkcs12 != null) {
                if (pkcs12pswd == null)
                    throw new IllegalArgumentException("ssl mode is enabled and pkcs12 is set, but password of pkcs12 file not specified. " +
                        "`pkcs12pswd $password`, example: pkcs12pswd pasSw0rD");
            } else if (certpem != null) {
                if (keypem == null)
                    throw new IllegalArgumentException("ssl mode is enabled and keypem is set, but keypem file not specified. " +
                        "`keypem $file_path`, example: keypem /etc/letsencrypt/live/example.com/privkey.pem");
            } else {
                throw new IllegalArgumentException("ssl mode is enabled, but neither pkcs12 and certpem not specified. " +
                    "`pkcs12 $file_path`, or, `certpem $file1,$file2,...`");
            }
        } else {
            if (pkcs12 != null || pkcs12pswd != null || certpem != null || keypem != null)
                throw new IllegalArgumentException("pkcs12 or pem info specified but no `ssl` flag set.");
        }
        if (pkcs12 != null) {
            pkcs12 = Utils.filename(pkcs12);
        }
        if (pkcs12 != null) {
            if (certpem != null || keypem != null) {
                throw new IllegalArgumentException("pkcs12 and pem cannot be used together");
            }
        }
        if (certpem != null) {
            //noinspection ConstantConditions
            if (pkcs12 != null || pkcs12pswd != null) {
                throw new IllegalArgumentException("pkcs12 and pem cannot be used together");
            }
        }
        if (port == redirectPort) {
            throw new IllegalArgumentException("listen port and redirectport are the same.");
        }
        if (!useKcp && udpOverTcpPort != -1) {
            throw new IllegalArgumentException("kcp is not enabled but uot is set");
        }

        assert Logger.lowLevelDebug("listen: " + port);
        assert Logger.lowLevelDebug("auth: " + auth);
        assert Logger.lowLevelDebug("ssl: " + ssl);
        assert Logger.lowLevelDebug("pkcs12: " + pkcs12);
        assert Logger.lowLevelDebug("pkcs12pswd: " + pkcs12pswd);
        assert Logger.lowLevelDebug("certpem: " + certpem);
        assert Logger.lowLevelDebug("keypem: " + keypem);
        assert Logger.lowLevelDebug("domain: " + domain);
        assert Logger.lowLevelDebug("redirectport: " + redirectPort);
        assert Logger.lowLevelDebug("useKcp: " + useKcp);
        assert Logger.lowLevelDebug("uot.port: " + udpOverTcpPort);
        assert Logger.lowLevelDebug("uot.nic: " + udpOverTcpNic);
        assert Logger.lowLevelDebug("webroot: " + webroot);

        // init event loops
        int threads = Math.min(4, Runtime.getRuntime().availableProcessors());
        if (VFDConfig.useFStack) {
            threads = 1;
        }
        int workers = threads;
        if (threads > 3) {
            workers -= 1; // one core for acceptor if there are at least 4 processors
        }
        if (udpOverTcpPort != -1) {
            workers = 1;
        }
        // initiate the acceptor event loop(s)
        EventLoopGroup acceptor = new EventLoopGroup("acceptor-group");
        acceptor.add("acceptor-loop");
        NetEventLoop netLoopForKcp = acceptor.next();
        SelectorEventLoop loopForKcp = netLoopForKcp.getSelectorEventLoop();
        // initiate the worker event loop(s)
        EventLoopGroup worker = new EventLoopGroup("worker-group");
        for (int i = 0; i < workers; ++i) {
            worker.add("worker-loop-" + i);
        }

        // init the listening server
        var listeningServerL4addr = new IPPort(IP.from(new byte[]{0, 0, 0, 0}), port);
        List<ServerSock> servers = new LinkedList<>();
        {
            ServerSock.checkBind(listeningServerL4addr);
            ServerSock server = ServerSock.create(listeningServerL4addr);
            servers.add(server);
        }
        if (useKcp) {
            ServerSock.checkBind(Protocol.UDP, listeningServerL4addr);
            KCPFDs kcpFDs = KCPFDs.getDefault();
            H2StreamedServerFDs serverFds = new H2StreamedServerFDs(kcpFDs, loopForKcp, listeningServerL4addr);
            ServerSock server = ServerSock.createUDP(listeningServerL4addr, loopForKcp, serverFds);
            servers.add(server);
        }
        if (udpOverTcpPort != -1) {
            var ipv4v6 = UdpOverTcpSetup.chooseIPs(udpOverTcpNic);
            var fds = UdpOverTcpSetup.setup(false, udpOverTcpPort, udpOverTcpNic, acceptor);
            // v4
            var v4ipport = new IPPort(ipv4v6.left, udpOverTcpPort);
            KCPFDs kcpFDs = new KCPFDs(KCPFDs.optionsFast4(), new UDPFDs(fds));
            H2StreamedServerFDs serverFds = new H2StreamedServerFDs(kcpFDs, loopForKcp, v4ipport);
            ServerSock server = ServerSock.createUDP(v4ipport, loopForKcp, serverFds);
            servers.add(server);
            // v6
            if (ipv4v6.right != null) {
                var v6ipport = new IPPort(ipv4v6.right, udpOverTcpPort);
                kcpFDs = new KCPFDs(KCPFDs.optionsFast4(), new UDPFDs(fds));
                serverFds = new H2StreamedServerFDs(kcpFDs, loopForKcp, v6ipport);
                server = ServerSock.createUDP(v6ipport, loopForKcp, serverFds);
                servers.add(server);
            }
        }
        ServerSock redirectServer = null;
        if (redirectPort != -1) {
            var l4addr = new IPPort(IP.from(new byte[]{0, 0, 0, 0}), redirectPort);
            redirectServer = ServerSock.create(l4addr);
        }

        // build ssl engine supplier (or maybe null)
        Supplier<SSLEngine> engineSupplier = null;
        if (ssl) {
            // init the ssl context
            if (certpem == null) {
                WebSocksUtils.initSslContext(pkcs12, pkcs12pswd,
                    "PKCS12", true, false);
            } else {
                String[] certs = certpem.split(",");
                for (String c : certs) {
                    if (c.isBlank()) {
                        throw new IllegalArgumentException("invalid input: cert file path is empty");
                    }
                }
                WebSocksUtils.initServerSslContextWithPem(certs, keypem);
            }

            // ssl params
            SSLParameters params = new SSLParameters();
            {
                params.setApplicationProtocols(new String[]{"http/1.1"});
                if (domain != null) {
                    String reg = "^" + domain.replaceAll("\\.", "\\\\.") + "$";
                    assert Logger.lowLevelDebug("the sni matcher regexp is " + reg);
                    params.setSNIMatchers(Collections.singletonList(SNIHostName.createSNIMatcher(reg)));
                }
            }

            // set some final variables for the lambda to use
            final String finalDomain = domain;
            final int finalPort = port;

            // only use one cert, so directly constructing the engine is ok
            if (domain == null) {
                engineSupplier = () -> {
                    SSLEngine engine = WebSocksUtils.createEngine();
                    engine.setUseClientMode(false);
                    engine.setSSLParameters(params);
                    return engine;
                };
            } else {
                engineSupplier = () -> {
                    SSLEngine engine = WebSocksUtils.createEngine(finalDomain, finalPort);
                    engine.setUseClientMode(false);
                    engine.setSSLParameters(params);
                    return engine;
                };
            }
        }
        // init the proxy server
        RedirectBaseInfo redirectBaseInfo = new RedirectBaseInfo(ssl ? "https" : "http", domain, port);
        WebSocksProtocolHandler webSocksProtocolHandler = new WebSocksProtocolHandler(auth, engineSupplier, webroot == null ? null : new WebRootPageProvider(webroot, redirectBaseInfo));
        ConnectorGen<WebSocksProxyContext> connGen = new ConnectorGen<>() {
            @Override
            public Type type() {
                return Type.handler;
            }

            @Override
            public ProtocolHandler<Tuple<WebSocksProxyContext, Callback<Connector, IOException>>> handler() {
                return webSocksProtocolHandler;
            }

            @Override
            public Connector genConnector(Connection accepted, Hint hint) {
                return null; // will not be called because type is `handler`
            }
        };
        for (ServerSock server : servers) {
            NetEventLoopProvider loopProvider;
            if (server.channel instanceof VirtualFD) {
                loopProvider = v -> netLoopForKcp;
            } else {
                loopProvider = worker::next;
            }
            Proxy proxy = new Proxy(
                new ProxyNetConfig()
                    .setAcceptLoop(netLoopForKcp)
                    .setInBufferSize(24576)
                    .setOutBufferSize(24576)
                    .setHandleLoopProvider(loopProvider)
                    .setServer(server)
                    .setConnGen(connGen),
                s -> {
                    // do nothing, won't happen
                    // when terminating, user should simply kill this process and won't close server
                });

            // start the proxy server
            proxy.handle();
        }

        if (redirectServer != null) {
            ProtocolServerHandler.apply(acceptor.get("acceptor-loop"), redirectServer,
                new ProtocolServerConfig().setInBufferSize(512).setOutBufferSize(512),
                new RedirectHandler(redirectBaseInfo));
        }

        Logger.alert("server started on " + port);
        if (redirectPort != -1) {
            Logger.alert("redirect server started on " + redirectPort);
        }
        if (useKcp) {
            Logger.alert("kcp server started on " + port);
        }
        if (udpOverTcpPort != -1) {
            Logger.alert("uot kcp server started on " + udpOverTcpPort);
        }
    }
}
