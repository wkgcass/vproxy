package vproxyx.websocks.relay;

import vproxy.component.elgroup.EventLoopGroup;
import vproxy.component.proxy.ConnectorGen;
import vproxy.component.proxy.Proxy;
import vproxy.component.proxy.ProxyNetConfig;
import vproxy.connection.*;
import vproxy.connection.util.SSLHandshakeDoneConnectableConnectionHandler;
import vproxy.dns.Resolver;
import vproxy.processor.Hint;
import vproxy.processor.Processor;
import vproxy.processor.ProcessorProvider;
import vproxy.processor.http1.HttpSubContext;
import vproxy.processor.http1.builder.HeaderBuilder;
import vproxy.processor.http1.builder.RequestBuilder;
import vproxy.protocol.ProtocolHandler;
import vproxy.protocol.ProtocolHandlerContext;
import vproxy.socks.AddressType;
import vproxy.util.*;
import vproxy.util.ringbuffer.ByteBufferRingBuffer;
import vproxy.util.ringbuffer.SSLUnwrapRingBuffer;
import vproxy.util.ringbuffer.SSLUtils;
import vproxy.util.ringbuffer.SSLWrapRingBuffer;
import vproxyx.websocks.*;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.function.BiConsumer;

public class RelayHttpsServer {
    private final WebSocksProxyAgentConnectorProvider connectorProvider;
    private final List<DomainChecker> httpsRelayDomains;
    private final List<DomainChecker> proxyHttpsRelayDomains;

    public RelayHttpsServer(WebSocksProxyAgentConnectorProvider connectorProvider, ConfigProcessor config) {
        this.connectorProvider = connectorProvider;
        httpsRelayDomains = config.getHTTPSRelayDomains();
        proxyHttpsRelayDomains = config.getProxyHTTPSRelayDomains();
    }

    public void launch(EventLoopGroup acceptor, EventLoopGroup worker) throws IOException {
        InetSocketAddress l4addr = new InetSocketAddress(Utils.l3addr("0.0.0.0"), 443);
        ServerSock.checkBind(l4addr);

        ServerSock server = ServerSock.create(l4addr);

        Proxy proxy = new Proxy(
            new ProxyNetConfig()
                .setAcceptLoop(acceptor.next())
                .setInBufferSize(24576)
                .setOutBufferSize(24576)
                .setHandleLoopProvider(worker::next)
                .setServer(server)
                .setConnGen(new RelayHttpsConnectorGen())
                .setSslContext(WebSocksUtils.getHTTPSRelaySSLContext())
                .setSslEngineManipulator(this::configureEngine),
            s -> {
                // do nothing, won't happen
                // when terminating, user should simply kill this process and won't close server
            });
        proxy.handle();
    }

    private void configureEngine(SSLEngine engine, SSLParameters parameters) {
        /*
         * JDK doc says:
         * The function's result is an application protocol name, or null to indicate that none of the advertised names are acceptable.
         * If the return value is an empty String then application protocol indications will not be used.
         * If the return value is null (no value chosen) or is a value that was not advertised by the peer,
         * the underlying protocol will determine what action to take.
         * (For example, ALPN will send a "no_application_protocol" alert and terminate the connection.)
         */
        // simply return http/1.1 here
        engine.setHandshakeApplicationProtocolSelector((en, proto) -> "http/1.1");
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

        private boolean handshakeDone(SSLEngine engine) {
            var status = engine.getHandshakeStatus();
            return status == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING || status == SSLEngineResult.HandshakeStatus.FINISHED;
        }

        @Override
        public void readable(ProtocolHandlerContext<Tuple<RelayHttpsProtocolContext, Callback<Connector, IOException>>> ctx) {
            if (ctx.data.left.errored) {
                return;
            }
            if (ctx.data.left.startConnection) { // new connection is starting, do not entry this method
                return;
            }

            SSLEngine engine = SSLUtils.getEngineFrom(ctx.connection);
            if (!handshakeDone(engine)) { // still handshaking, ignore
                return;
            }
            if (ctx.inBuffer.used() == 0) {
                assert Logger.lowLevelDebug("no data for now, do not consume");
                return;
            }

            String hostname = SSLUtils.getSNI(ctx.inBuffer);

            if (hostname == null) {
                Logger.warn(LogType.ALERT, "SNI not retrieved, try to parse the cleartext data using http/1.x");
                ByteArray data = SSLUtils.getPlainBufferBytes((SSLUnwrapRingBuffer) ctx.inBuffer);

                // ok, let's parse the data then ...
                // assume it's http
                {
                    Processor p = ProcessorProvider.getInstance().get("http/1.x");
                    Processor.Context c = p.init(null);
                    //noinspection unchecked
                    Processor.SubContext s = p.initSub(c, 0, null);
                    HttpSubContext sctx = (HttpSubContext) s;
                    sctx.setParserMode();

                    out:
                    for (int i = 0; i < data.length(); ++i) {
                        try {
                            sctx.feed(data.get(i));
                        } catch (Exception e) {
                            Logger.error(LogType.INVALID_EXTERNAL_DATA, "invalid request, or is not http", e);
                            ctx.data.left.errored = true;
                            ctx.data.right.failed(new IOException(e));
                            return;
                        }
                        RequestBuilder r = sctx.getParsingReq();
                        if (r != null) {
                            if (r.headers != null) {
                                for (HeaderBuilder h : r.headers) {
                                    if (h.value != null) {
                                        if (h.key.toString().toLowerCase().trim().equals("host")) {
                                            hostname = h.value.toString().trim(); // hostname retrieved
                                            break out;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (hostname == null) {
                String msg = "neither sni nor host header provided";
                ctx.data.left.errored = true;
                ctx.data.right.failed(new IOException(msg));
                return;
            }
            ctx.data.left.startConnection = true;
            handle(ctx, hostname);
        }

        private void handle(ProtocolHandlerContext<Tuple<RelayHttpsProtocolContext, Callback<Connector, IOException>>> ctx, String hostname) {
            for (DomainChecker chk : proxyHttpsRelayDomains) { // proxy relay will cover more conditions than direct relay
                if (chk.needProxy(hostname, 443)) {
                    handleProxy(ctx, hostname);
                    return;
                }
            }
            for (DomainChecker chk : httpsRelayDomains) {
                if (chk.needProxy(hostname, 443)) {
                    handleRelay(ctx, hostname);
                    return;
                }
            }
            // cannot handle the condition
            ctx.data.left.errored = true;
            ctx.data.right.failed(new IOException("unexpected request for " + hostname));
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
                if (!(connector instanceof AlreadyConnectedConnector)) {
                    Logger.error(LogType.IMPROPER_USE, "The request domain will not be proxied " + hostname + ", configuration must be wrong");
                    ctx.data.right.failed(new IOException("The request domain will not be proxied"));
                    return;
                }

                // OK, here comes a ssl buffer wrapping another ssl buffer:
                /*
                 *                                                                        WebSocksWrap
                 *                                                            +----------------+
                 *                    HttpsUnwrap               InterTLSWrap  |                |
                 *                      +-------------+               +-------|-------+        |
                 *                      | +---+ +---+ |               | +---+ | +---+ | +----+ |
                 *              in ------>| E | | P |-----------plain-->| P |-->| E |-->| EE |------->out
                 *                      | +---+ +---+ |               | +---+ | +---+ | +----+ |
                 *                      +-------------+               +-------|-------+        |
                 *                                                            |                |
                 *                                                            +----------------+
                 *
                 *                                                                        WebSocksUnwrap
                 *                                                            +----------------+
                 *                    HttpsWrap               InterTLSUnwrap  |                |
                 *                      +-------------+               +-------|-------+        |
                 *                      | +---+ +---+ |               | +---+ | +---+ | +----+ |
                 *             out <------| E | | P |<---plain----------| P |<--| E |<--| EE |<------in
                 *                      | +---+ +---+ |               | +---+ | +---+ | +----+ |
                 *                      +-------------+               +-------|-------+        |
                 *                                                            |                |
                 *                                                            +----------------+
                 */

                SSLEngine engine = WebSocksUtils.createEngine(hostname, 443);
                engine.setUseClientMode(true);
                engine.setHandshakeApplicationProtocolSelector((e, ls) -> "http/1.1");

                ByteBufferRingBuffer bufI = SSLUtils.getPlainBuffer((SSLUnwrapRingBuffer) ctx.connection.getInBuffer());
                ByteBufferRingBuffer bufO = SSLUtils.getPlainBuffer((SSLWrapRingBuffer) ctx.connection.getOutBuffer());

                // built the inter(mediate) buffers
                SSLUtils.SSLBufferPair pair = SSLUtils.genbuf(engine, bufO, bufI,
                    ctx.connection.getEventLoop().getSelectorEventLoop());
                SSLUnwrapRingBuffer interTLSUnwrap = pair.left;
                SSLWrapRingBuffer interTLSWrap = pair.right;

                RingBuffer interTLSUnwrapEncryptedBuffer = SSLUtils.getEncryptedBuffer(interTLSUnwrap);
                RingBuffer interTLSWrapEncryptedBuffer = SSLUtils.getEncryptedBuffer(interTLSWrap);

                ConnectableConnection websocksConn = ((AlreadyConnectedConnector) connector).getConnection();

                try {
                    websocksConn.UNSAFE_replaceBuffer(interTLSUnwrapEncryptedBuffer, interTLSWrapEncryptedBuffer);
                } catch (IOException e) {
                    Logger.shouldNotHappen("replace buffers failed", e);
                    ctx.data.left.errored = true;
                    ctx.data.right.failed(e);
                    return;
                }

                if (websocksConn.getInBuffer() instanceof SSLUnwrapRingBuffer) {
                    // register an event to let the `E` buffer unwrap data to `P` when `EE -> E` triggers
                    SSLUtils.unwrapAfterWritingToEncryptedBuffer(interTLSUnwrap, (SSLUnwrapRingBuffer) websocksConn.getInBuffer());
                }

                assert Logger.lowLevelDebug("proxy https relay is going to make a callback");
                ctx.data.left.finished = true;
                ctx.data.right.succeeded(new SupplierConnector(websocksConn.remote, websocksConn, ctx.connection.getEventLoop()));
            });
        }

        private void handleRelay(ProtocolHandlerContext<Tuple<RelayHttpsProtocolContext, Callback<Connector, IOException>>> ctx, String hostname) {
            Logger.alert("[RELAY] direct https relay for " + hostname);

            //noinspection unchecked
            BiConsumer<String, Callback> resolveF = (a, b) -> Resolver.getDefault().resolve(a, b);
            if (WebSocksUtils.httpDNSServer != null) {
                //noinspection unchecked
                resolveF = (a, b) -> WebSocksUtils.httpDNSServer.resolve(a, b);
            }
            final NetEventLoop loop = ctx.connection.getEventLoop();
            final String finalHostname = hostname;
            resolveF.accept(hostname, new Callback() {
                @Override
                protected void onSucceeded(Object o) {
                    InetAddress value = (InetAddress) o;

                    SSLEngine engine = WebSocksUtils.createEngine();
                    engine.setUseClientMode(true);
                    engine.setHandshakeApplicationProtocolSelector((e, ls) -> "http/1.1");
                    SSLUtils.SSLBufferPair pair = SSLUtils.genbuf(engine, RingBuffer.allocate(24576), RingBuffer.allocate(24576), loop.getSelectorEventLoop());
                    ConnectableConnection conn;
                    try {
                        conn = ConnectableConnection.create(new InetSocketAddress(value, 443), new ConnectionOpts().setTimeout(60_000),
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
                                ctx.data.left.finished = true;
                                ctx.data.right.succeeded(new AlreadyConnectedConnector(conn.remote, conn, loop));
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
                protected void onFailed(Throwable o) {
                    UnknownHostException err = (UnknownHostException) o;
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
