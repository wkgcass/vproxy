package vproxyx.websocks;

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
import vproxy.util.*;
import vproxy.util.nio.ByteArrayChannel;
import vproxy.util.ringbuffer.SSLUnwrapRingBuffer;
import vproxy.util.ringbuffer.SSLUtils;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.function.BiConsumer;

public class RelayHttpsServer {
    private RelayHttpsServer() {
    }

    public static void launch(EventLoopGroup acceptor, EventLoopGroup worker) throws IOException {
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
                .setSslContext(WebSocksUtils.getTlsRelaySSLContext())
                .setSslEngineManipulator(RelayHttpsServer::configureEngine),
            s -> {
                // do nothing, won't happen
                // when terminating, user should simply kill this process and won't close server
            });
        proxy.handle();
    }

    private static void configureEngine(SSLEngine engine, SSLParameters parameters) {
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

    private static class RelayHttpsConnectorGen implements ConnectorGen<Void> {
        @Override
        public Connector genConnector(Connection accepted, Hint hint) { // will not be called
            return null;
        }

        @Override
        public Type type() {
            return Type.handler;
        }

        @Override
        public ProtocolHandler<Tuple<Void, Callback<Connector, IOException>>> handler() {
            return new RelayHttpsProtocolHandler();
        }
    }

    private static class RelayHttpsProtocolHandler implements ProtocolHandler<Tuple<Void, Callback<Connector, IOException>>> {
        private boolean startConnection = false;
        private boolean finished = false;
        private boolean errored = false;

        @Override
        public void init(ProtocolHandlerContext<Tuple<Void, Callback<Connector, IOException>>> ctx) {
            ctx.data = new Tuple<>(null, null);
        }

        private boolean handshakeDone(SSLEngine engine) {
            var status = engine.getHandshakeStatus();
            return status == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING || status == SSLEngineResult.HandshakeStatus.FINISHED;
        }

        @Override
        public void readable(ProtocolHandlerContext<Tuple<Void, Callback<Connector, IOException>>> ctx) {
            if (errored) {
                return;
            }
            if (startConnection) { // new connection is starting, do not entry this method
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

            String hostname = null;

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
                        errored = true;
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

            if (hostname == null) {
                String msg = "sni not provided";
                errored = true;
                ctx.data.right.failed(new IOException(msg));
                return;
            }
            startConnection = true;
            Logger.alert("[HTTPS] direct https relay for " + hostname);

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
                    SSLUtils.SSLBufferPair pair = SSLUtils.genbuf(engine, RingBuffer.allocate(24576), RingBuffer.allocate(24576), loop.getSelectorEventLoop());
                    ConnectableConnection conn;
                    try {
                        conn = ConnectableConnection.create(new InetSocketAddress(value, 443), new ConnectionOpts().setTimeout(60_000),
                            pair.left, pair.right);
                    } catch (IOException e) {
                        Logger.error(LogType.CONN_ERROR, "connecting to " + finalHostname + "(" + value + "):443 failed");
                        errored = true;
                        ctx.data.right.failed(e);
                        return;
                    }
                    // wait until handshake done
                    try {
                        loop.addConnectableConnection(conn, null, new SSLHandshakeDoneConnectableConnectionHandler(
                            engine, new Callback<>() {
                            @Override
                            protected void onSucceeded(Void value) {
                                finished = true;
                                ctx.data.right.succeeded(new AlreadyConnectedConnector(conn.remote, conn, loop));
                            }

                            @Override
                            protected void onFailed(IOException err) {
                                errored = true;
                                ctx.data.right.failed(err);
                            }
                        }
                        ));
                    } catch (IOException e) {
                        Logger.error(LogType.EVENT_LOOP_ADD_FAIL, "adding conn for https direct relay into event loop failed", e);
                        errored = true;
                        ctx.data.right.failed(e);
                    }
                }

                @Override
                protected void onFailed(Throwable o) {
                    UnknownHostException err = (UnknownHostException) o;
                    Logger.error(LogType.CONN_ERROR, "resolve for " + finalHostname + " failed in https direct relay", err);
                    errored = true;
                    ctx.data.right.failed(err);
                }
            });
        }

        @Override
        public void exception(ProtocolHandlerContext<Tuple<Void, Callback<Connector, IOException>>> ctx, Throwable err) {
            if (errored) {
                return;
            }
            if (finished) {
                return; // do nothing
            }
            ctx.connection.close();
            errored = true;
            ctx.data.right.failed(new IOException(err));
        }

        @Override
        public void end(ProtocolHandlerContext<Tuple<Void, Callback<Connector, IOException>>> ctx) {
            if (errored) {
                return;
            }
            if (finished) {
                return; // do nothing
            }
            errored = true;
            ctx.data.right.failed(new IOException("connection closed while handshaking"));
        }

        @Override
        public boolean closeOnRemoval(ProtocolHandlerContext<Tuple<Void, Callback<Connector, IOException>>> ctx) {
            // close if it's not finished
            return !finished;
        }
    }
}
