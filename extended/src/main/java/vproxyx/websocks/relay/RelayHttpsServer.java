package vproxyx.websocks.relay;

import vfd.IP;
import vfd.IPPort;
import vproxy.component.proxy.ConnectorGen;
import vproxy.component.proxy.Proxy;
import vproxy.component.proxy.ProxyNetConfig;
import vproxybase.socks.AddressType;
import vproxybase.component.elgroup.EventLoopGroup;
import vproxybase.connection.*;
import vproxybase.connection.util.SSLHandshakeDoneConnectableConnectionHandler;
import vproxybase.processor.Hint;
import vproxybase.protocol.ProtocolHandler;
import vproxybase.protocol.ProtocolHandlerContext;
import vproxybase.util.*;
import vproxybase.util.ringbuffer.SSLUnwrapRingBuffer;
import vproxybase.util.ringbuffer.SSLUtils;
import vproxyx.websocks.ConfigProcessor;
import vproxyx.websocks.DomainChecker;
import vproxyx.websocks.WebSocksProxyAgentConnectorProvider;
import vproxyx.websocks.WebSocksUtils;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class RelayHttpsServer {
    private final WebSocksProxyAgentConnectorProvider connectorProvider;
    private final List<DomainChecker> httpsSniErasureDomains;
    private final List<DomainChecker> proxyDomains;

    public RelayHttpsServer(WebSocksProxyAgentConnectorProvider connectorProvider, ConfigProcessor config) {
        this.connectorProvider = connectorProvider;
        httpsSniErasureDomains = config.getHttpsSniErasureDomains();
        proxyDomains = new LinkedList<>();
        for (List<DomainChecker> domains : config.getDomains().values()) {
            proxyDomains.addAll(domains);
        }
    }

    public Proxy launch(EventLoopGroup acceptor, EventLoopGroup worker) throws IOException {
        IPPort l4addr = new IPPort(IP.from("0.0.0.0"), 443);
        ServerSock.checkBind(l4addr);

        ServerSock server = ServerSock.create(l4addr);

        Proxy proxy = new Proxy(
            new ProxyNetConfig()
                .setAcceptLoop(acceptor.next())
                .setInBufferSize(24576)
                .setOutBufferSize(24576)
                .setHandleLoopProvider(worker::next)
                .setServer(server)
                .setConnGen(new RelayHttpsConnectorGen()),
            s -> {
                Logger.warn(LogType.ALERT, "closing server " + l4addr);
                server.close();
            });
        proxy.handle();

        return proxy;
    }

    private class RelayHttpsConnectorGen implements ConnectorGen<RelayHttpsProtocolContext> {
        @Override
        public Connector genConnector(Connection accepted, Hint hint) { // will not be called
            return null;
        }

        @Override
        public Type type() {
            return Type.handler;
        }

        @Override
        public ProtocolHandler<Tuple<RelayHttpsProtocolContext, Callback<Connector, IOException>>> handler() {
            return new RelayHttpsProtocolHandler();
        }
    }

    private static class RelayHttpsProtocolContext {
        private boolean startConnection = false;
        private boolean finished = false;
        private boolean errored = false;
    }

    private class RelayHttpsProtocolHandler implements ProtocolHandler<Tuple<RelayHttpsProtocolContext, Callback<Connector, IOException>>> {
        @Override
        public void init(ProtocolHandlerContext<Tuple<RelayHttpsProtocolContext, Callback<Connector, IOException>>> ctx) {
            ctx.data = new Tuple<>(new RelayHttpsProtocolContext(), null);
        }

        @Override
        public void readable(ProtocolHandlerContext<Tuple<RelayHttpsProtocolContext, Callback<Connector, IOException>>> ctx) {
            if (ctx.data.left.errored) {
                return;
            }
            if (ctx.data.left.startConnection) { // new connection is starting, do not entry this method
                return;
            }

            String sni;
            {
                String[] arrSni = new String[1];
                IOException err = SSLHelper.extractSniFromClientHello(ctx.inBuffer, arrSni);
                if (err != null) {
                    ctx.data.left.errored = true;
                    ctx.data.right.failed(err);
                    return;
                }
                sni = arrSni[0];
            }

            ctx.data.left.startConnection = true;
            handle(ctx, sni);
        }

        private void handle(ProtocolHandlerContext<Tuple<RelayHttpsProtocolContext, Callback<Connector, IOException>>> ctx, String hostname) {
            for (DomainChecker chk : httpsSniErasureDomains) {
                if (chk.needProxy(hostname, 443)) {
                    // get alpn
                    String[][] alpn = new String[1][];
                    {
                        IOException err = SSLHelper.extractAlpnFromClientHello(ctx.inBuffer, alpn);
                        if (err != null) {
                            ctx.data.left.errored = true;
                            ctx.data.right.failed(err);
                            return;
                        }
                    }

                    Logger.alert("[CLIENT_HELLO] sni = " + hostname + ", alpn = " + (alpn[0] == null ? "null" : Arrays.toString(alpn[0])));
                    handleRelay(ctx, hostname, alpn[0]);
                    return;
                }
            }

            Logger.alert("[CLIENT_HELLO] sni = " + hostname);
            for (DomainChecker chk : proxyDomains) { // proxy relay may cover more conditions than direct relay
                if (chk.needProxy(hostname, 443)) {
                    handleProxy(ctx, hostname);
                    return;
                }
            }
            // cannot handle the condition
            ctx.data.left.errored = true;
            ctx.data.right.failed(new IOException("unexpected request for " + hostname + ", the domain is not relayed nor proxied"));
        }

        private void handleProxy(ProtocolHandlerContext<Tuple<RelayHttpsProtocolContext, Callback<Connector, IOException>>> ctx, String hostname) {
            // no need to log the proxy event
            // will be logged in the connector provider
            connectorProvider.provide(ctx.connection, AddressType.domain, hostname, 443, connector -> {
                assert Logger.lowLevelDebug("proxy https relay got a connector: " + connector);
                if (ctx.data.left.errored) {
                    if (connector != null) {
                        assert Logger.lowLevelDebug("the connector successfully retrieved but the accepted connection errored, so close the connector");
                        connector.close();
                    }
                    return;
                }
                if (connector == null) {
                    assert Logger.lowLevelDebug("no available remote server connector for now");
                    ctx.data.right.failed(new IOException("no available remote server connector"));
                    return;
                }

                assert Logger.lowLevelDebug("proxy https relay is going to make a callback");
                ctx.data.left.finished = true;
                ctx.data.right.succeeded(connector);
            });
        }

        private void handleRelay(ProtocolHandlerContext<Tuple<RelayHttpsProtocolContext, Callback<Connector, IOException>>> ctx, String hostname, String[] alpn) {
            Logger.alert("[HTTPS SNI ERASURE] direct https relay for " + hostname);

            final NetEventLoop loop = ctx.connection.getEventLoop();
            final String finalHostname = hostname;
            WebSocksUtils.agentDNSServer.resolve(hostname, new Callback<>() {
                @Override
                protected void onSucceeded(IP value) {
                    SSLEngine engine = WebSocksUtils.createEngine();
                    engine.setUseClientMode(true);
                    SSLParameters sslParams = new SSLParameters();
                    if (alpn != null) {
                        sslParams.setApplicationProtocols(alpn);
                    }
                    engine.setSSLParameters(sslParams);

                    var remote = new IPPort(value, 443);
                    SSLUtils.SSLBufferPair pair = SSLUtils.genbuf(
                        engine,
                        RingBuffer.allocate(24576),
                        RingBuffer.allocate(24576),
                        loop.getSelectorEventLoop(),
                        remote);
                    ConnectableConnection conn;
                    try {
                        conn = ConnectableConnection.create(remote, new ConnectionOpts().setTimeout(60_000),
                            pair.left, pair.right);
                    } catch (IOException e) {
                        Logger.error(LogType.CONN_ERROR, "connecting to " + finalHostname + "(" + value + "):443 failed");
                        ctx.data.left.errored = true;
                        ctx.data.right.failed(e);
                        return;
                    }
                    // wait until handshake done
                    try {
                        loop.addConnectableConnection(conn, null, new SSLHandshakeDoneConnectableConnectionHandler(
                            engine, new Callback<>() {
                            @Override
                            protected void onSucceeded(Void value) {
                                String selectedAlpn;
                                try {
                                    selectedAlpn = engine.getApplicationProtocol();
                                } catch (UnsupportedOperationException e) {
                                    String msg = "engine.getApplicationProtocol is not supported";
                                    Logger.shouldNotHappen(msg, e);
                                    ctx.data.left.errored = true;
                                    ctx.data.right.failed(new IOException(msg));
                                    return;
                                }
                                Logger.alert("[SERVER_HELLO] from " + hostname + ", alpn = " + selectedAlpn);
                                ctx.data.left.finished = true;
                                ctx.data.right.succeeded(new HttpsSniErasureForRawAcceptedConnector(conn.remote, conn, loop, selectedAlpn));
                            }

                            @Override
                            protected void onFailed(IOException err) {
                                ctx.data.left.errored = true;
                                ctx.data.right.failed(err);
                            }
                        }
                        ));
                    } catch (IOException e) {
                        Logger.error(LogType.EVENT_LOOP_ADD_FAIL, "adding conn for https direct relay into event loop failed", e);
                        ctx.data.left.errored = true;
                        ctx.data.right.failed(e);
                    }
                }

                @Override
                protected void onFailed(UnknownHostException err) {
                    Logger.error(LogType.CONN_ERROR, "resolve for " + finalHostname + " failed in https direct relay", err);
                    ctx.data.left.errored = true;
                    ctx.data.right.failed(err);
                }
            });
        }

        @Override
        public void exception(ProtocolHandlerContext<Tuple<RelayHttpsProtocolContext, Callback<Connector, IOException>>> ctx, Throwable err) {
            if (ctx.data.left.errored) {
                return;
            }
            if (ctx.data.left.finished) {
                return; // do nothing
            }
            ctx.connection.close();
            ctx.data.left.errored = true;
            ctx.data.right.failed(new IOException(err));
        }

        @Override
        public void end(ProtocolHandlerContext<Tuple<RelayHttpsProtocolContext, Callback<Connector, IOException>>> ctx) {
            if (ctx.data.left.errored) {
                return;
            }
            if (ctx.data.left.finished) {
                return; // do nothing
            }
            ctx.data.left.errored = true;
            if (ctx.inBuffer instanceof SSLUnwrapRingBuffer) {
                Logger.error(LogType.CONN_ERROR, "connection closed while handshaking, sni of the ssl connection is " + ((SSLUnwrapRingBuffer) ctx.inBuffer).getSni());
            }
            ctx.data.right.failed(new IOException("connection closed while handshaking"));
        }

        @Override
        public boolean closeOnRemoval(ProtocolHandlerContext<Tuple<RelayHttpsProtocolContext, Callback<Connector, IOException>>> ctx) {
            // close if it's not finished
            return !ctx.data.left.finished;
        }
    }
}
