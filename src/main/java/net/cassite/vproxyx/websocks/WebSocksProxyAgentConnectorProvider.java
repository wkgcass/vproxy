package net.cassite.vproxyx.websocks;

import net.cassite.vproxy.component.svrgroup.ServerGroup;
import net.cassite.vproxy.component.svrgroup.SvrHandleConnector;
import net.cassite.vproxy.connection.*;
import net.cassite.vproxy.http.HttpResp;
import net.cassite.vproxy.http.HttpRespParser;
import net.cassite.vproxy.pool.ConnectionPool;
import net.cassite.vproxy.pool.ConnectionPoolHandler;
import net.cassite.vproxy.pool.PoolCallback;
import net.cassite.vproxy.selector.SelectorEventLoop;
import net.cassite.vproxy.socks.AddressType;
import net.cassite.vproxy.socks.Socks5ConnectorProvider;
import net.cassite.vproxy.util.*;
import net.cassite.vproxy.util.ringbuffer.SSLUtils;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

public class WebSocksProxyAgentConnectorProvider implements Socks5ConnectorProvider {
    class WebSocksPoolHandler implements ConnectionPoolHandler {
        class WebSocksClientHandshakeHandler implements ClientConnectionHandler {
            private final String domainOfProxy;
            private final HttpRespParser httpRespParser = new HttpRespParser(false);

            WebSocksClientHandshakeHandler(String domainOfProxy) {
                this.domainOfProxy = domainOfProxy;
            }

            @Override
            public void connected(ClientConnectionHandlerContext ctx) {
                CommonProcess.sendUpgrade(ctx, domainOfProxy, user, pass);
            }

            @Override
            public void readable(ConnectionHandlerContext ctx) {
                CommonProcess.parseUpgradeResp(ctx, httpRespParser,
                    /* fail */() -> {
                        assert Logger.lowLevelDebug("handshake for the pool failed");
                        cb.connectionError((ClientConnection) ctx.connection);
                    },
                    /* succ */() -> {
                        assert Logger.lowLevelDebug("handshake for the pool succeeded");
                        cb.handshakeDone((ClientConnection) ctx.connection);
                    });
            }

            @Override
            public void writable(ConnectionHandlerContext ctx) {
                // do nothing, the buffer is large enough for handshaking
            }

            @Override
            public void exception(ConnectionHandlerContext ctx, IOException err) {
                Logger.error(LogType.CONN_ERROR, "conn " + ctx.connection +
                    " got exception when handshaking for the pool", err);
                cb.connectionError((ClientConnection) ctx.connection);
            }

            @Override
            public void closed(ConnectionHandlerContext ctx) {
                Logger.error(LogType.CONN_ERROR, "conn " + ctx.connection +
                    " closed when handshaking for the pool");
                cb.connectionError((ClientConnection) ctx.connection);
            }

            @Override
            public void removed(ConnectionHandlerContext ctx) {
                // ignore
            }
        }

        private final String alias;
        private final PoolCallback cb;

        WebSocksPoolHandler(String alias, PoolCallback cb) {
            this.alias = alias;
            this.cb = cb;
        }

        @Override
        public ClientConnection provide(NetEventLoop loop) {
            SvrHandleConnector connector = servers.get(alias).next(null/*we ignore the source because it's wrr*/);
            if (connector == null) {
                assert Logger.lowLevelDebug("no available remote server connector for now");
                return null;
            }
            boolean useSSL = (boolean) connector.getData(); /*useSSL, see ConfigProcessor*/
            ClientConnection conn;
            try {
                if (useSSL) {
                    conn = CommonProcess.makeSSLConnection(connector);
                } else {
                    conn = connector.connect(
                        WebSocksUtils.getConnectionOpts(),
                        RingBuffer.allocateDirect(16384), RingBuffer.allocateDirect(16384));
                }
            } catch (IOException e) {
                Logger.error(LogType.CONN_ERROR, "make websocks connection for the pool failed", e);
                return null;
            }

            try {
                loop.addClientConnection(conn, null, new WebSocksClientHandshakeHandler(connector.getHostName()));
            } catch (IOException e) {
                conn.close();
                return null;
            }
            return conn;
        }

        @Override
        public void keepaliveReadable(ClientConnection conn) {
            // do nothing, we do not expect any data
        }

        @Override
        public void keepalive(ClientConnection conn) {
            // PONG frame
            byte[] bytes = {
                (byte) 0b10001010, // FIN and %xA
                0, // mask=0 and paylod=0
            };
            ByteArrayChannel chnl = ByteArrayChannel.fromFull(bytes);
            conn.getOutBuffer().storeBytesFrom(chnl);
            assert Logger.lowLevelDebug("sending PONG frame");
        }
    }

    class AgentClientConnectionHandler implements ClientConnectionHandler {
        private final String domainOfProxy;
        private final AddressType addressType;
        private final String domain;
        private final int port;
        private final Consumer<Connector> providedCallback;

        private final boolean usePooledConnection;

        // 0: init,
        // 1: expecting http resp,
        // 2: expecting WebSocket resp,
        // 3: expecting socks5 auth method exchange
        // 4: preserved for socks5 auth result
        // 5: expecting socks5 connect result first 4 bytes
        // 6: expecting socks5 connect result
        private int step = 0;
        private HttpRespParser httpRespParser;
        private ByteArrayChannel webSocketFrame;
        private ByteArrayChannel socks5AuthMethodExchange;
        private ByteArrayChannel socks5ConnectResult;

        AgentClientConnectionHandler(String domainOfProxy,
                                     AddressType addressType,
                                     String domain,
                                     int port,
                                     Consumer<Connector> providedCallback,
                                     boolean usePooledConnection) {
            this.domainOfProxy = domainOfProxy;
            this.addressType = addressType;
            this.domain = domain;
            this.port = port;
            this.providedCallback = providedCallback;

            this.usePooledConnection = usePooledConnection;
        }

        private void utilAlertFail(ConnectionHandlerContext ctx) {
            providedCallback.accept(null);
            ctx.connection.close();
        }

        @Override
        public void connected(ClientConnectionHandlerContext ctx) {
            if (!usePooledConnection) {
                // start handshaking if the connection is not pooled
                CommonProcess.sendUpgrade(ctx, domainOfProxy, user, pass);

                step = 1;
                httpRespParser = new HttpRespParser(false);
            } else {
                // otherwise, send the WebSocket frame
                sendWebSocketFrame(ctx);

                step = 2;
            }
        }

        @Override
        public void readable(ConnectionHandlerContext ctx) {
            if (step == 1) {
                CommonProcess.parseUpgradeResp(ctx, httpRespParser,
                    /* fail */ () -> utilAlertFail(ctx),
                    /* succ */ () -> {
                        // remove the parser
                        httpRespParser = null;

                        sendWebSocketFrame(ctx);
                    });
            } else if (step == 2) {
                // WebSocket
                ctx.connection.getInBuffer().writeTo(webSocketFrame);
                if (webSocketFrame.free() != 0) {
                    return; // still have data to read
                }
                // remove the chnl
                webSocketFrame = null;
                if (strictMode) {
                    // start socks5 negotiation
                    assert Logger.lowLevelDebug("strictMode is on, send socks5 auth here");
                    sendSocks5AuthMethodExchange(ctx);
                }
                step = 3;
                byte[] ex = new byte[2];
                socks5AuthMethodExchange = ByteArrayChannel.fromEmpty(ex);
            } else if (step == 3) {
                // socks5 auth method respond
                ctx.connection.getInBuffer().writeTo(socks5AuthMethodExchange);
                if (socks5AuthMethodExchange.free() != 0) {
                    return; // still have data to read
                }
                // process the resp
                checkAndProcessAuthExchangeAndSendConnect(ctx);
            } else if (step == 5) {
                // socks5 connected respond first 5 bytes
                ctx.connection.getInBuffer().writeTo(socks5ConnectResult);
                if (socks5ConnectResult.free() != 0) {
                    return; // still have data to read
                }
                // process the resp
                checkAndProcessFirst5BytesOfConnectResult(ctx);
            } else {
                // left bytes for socks5
                ctx.connection.getInBuffer().writeTo(socks5ConnectResult);
                if (socks5ConnectResult.free() != 0) {
                    return; // still have data to read
                }
                // check inBuffer
                if (ctx.connection.getInBuffer().used() != 0) {
                    // still got data
                    Logger.error(LogType.INVALID_EXTERNAL_DATA,
                        "in buffer still have data after socks5 connect response " + ctx.connection.getInBuffer().toString());
                    utilAlertFail(ctx);
                    return;
                }
                // process done
                done(ctx);
                return;
            }

            if (!ctx.connection.isClosed() && ctx.connection.getInBuffer().used() != 0) {
                // we may proceed
                readable(ctx);
            }
        }

        private void sendWebSocketFrame(ConnectionHandlerContext ctx) {
            // prepare for web socket recv
            step = 2;
            // expecting to read the exactly same data as sent
            byte[] bytes = new byte[WebSocksUtils.bytesToSendForWebSocketFrame.length];
            webSocketFrame = ByteArrayChannel.fromEmpty(bytes);

            // send WebSocket frame:
            if (strictMode) {
                assert Logger.lowLevelDebug("strictMode is on, only send the websocket frame");
                WebSocksUtils.sendWebSocketFrame(ctx.connection.getOutBuffer());
            } else {
                assert Logger.lowLevelDebug("strictMode is off, send all packets");
                // if not strict, we can send socks5 negotiation also
                Set<RingBufferETHandler> handlers = ctx.connection.getOutBuffer().getHandlers();
                // remove all handlers first
                for (RingBufferETHandler h : handlers) {
                    ctx.connection.getOutBuffer().removeHandler(h);
                }
                WebSocksUtils.sendWebSocketFrame(ctx.connection.getOutBuffer());
                sendSocks5AuthMethodExchange(ctx);
                sendSocks5Connect(ctx);
                // and add them back
                for (RingBufferETHandler h : handlers) {
                    ctx.connection.getOutBuffer().addHandler(h);
                }
                // trigger the readable
                assert Logger.lowLevelDebug("manually trigger the readable event for " + handlers.size() + " times");
                for (RingBufferETHandler h : handlers) {
                    h.readableET();
                }

                // in this way, all these data will be sent in the same tcp packet
            }
        }

        private void sendSocks5AuthMethodExchange(ConnectionHandlerContext ctx) {
            byte[] toSend = {
                5, // version
                1, // cound
                0, // no auth
            };
            ByteArrayChannel chnl = ByteArrayChannel.fromFull(toSend);
            ctx.connection.getOutBuffer().storeBytesFrom(chnl);
        }

        private void sendSocks5Connect(ConnectionHandlerContext ctx) {
            // build message to send

            byte[] chars = domain.getBytes();

            int len;
            switch (addressType) {
                case ipv4:
                    len = 1 + 1 + 1 + 1 + 4 + 2;
                    break;
                case domain:
                    len = 1 + 1 + 1 + 1 + (1 + chars.length) + 2;
                    break;
                case ipv6:
                    len = 1 + 1 + 1 + 1 + 16 + 2;
                    break;
                default:
                    Logger.shouldNotHappen("unknown socks address type: " + addressType);
                    utilAlertFail(ctx);
                    return;
            }
            byte[] toSend = new byte[len];
            toSend[0] = 5; // version
            toSend[1] = 1; // connect
            toSend[2] = 0; // preserved
            toSend[3] = addressType.code; // type
            //---
            toSend[toSend.length - 2] = (byte) ((port >> 8) & 0xff);
            toSend[toSend.length - 1] = (byte) (port & 0xff);

            switch (addressType) {
                case ipv4:
                    byte[] v4 = Utils.parseIpv4StringConsiderV6Compatible(domain);
                    if (v4 == null) {
                        Logger.shouldNotHappen("the socks lib produces an invalid ipv4: " + domain);
                        utilAlertFail(ctx);
                        return;
                    }
                    System.arraycopy(v4, 0, toSend, 4, 4);
                    break;
                case domain:
                    toSend[4] = (byte) domain.length(); // domain length
                    System.arraycopy(chars, 0, toSend, 5, chars.length);
                    break;
                case ipv6:
                    byte[] v6 = Utils.parseIpv4StringConsiderV6Compatible(domain);
                    if (v6 == null) {
                        Logger.shouldNotHappen("the socks lib produces an invalid ipv6: " + domain);
                        utilAlertFail(ctx);
                        return;
                    }
                    System.arraycopy(v6, 0, toSend, 4, 16);
                    break;
                default:
                    Logger.shouldNotHappen("unknown socks address type: " + addressType);
                    utilAlertFail(ctx);
                    return;
            }

            ByteArrayChannel chnl = ByteArrayChannel.fromFull(toSend);
            ctx.connection.getOutBuffer().storeBytesFrom(chnl);
        }

        private void checkAndProcessAuthExchangeAndSendConnect(ConnectionHandlerContext ctx) {
            byte[] ex = socks5AuthMethodExchange.get();
            if (ex[0] != 5 || ex[1] != 0) {
                // version != 5 or meth != no_auth
                Logger.error(LogType.INVALID_EXTERNAL_DATA,
                    "response version is wrong or method is wrong: " + ex[0] + "," + ex[1]);
                utilAlertFail(ctx);
                return;
            }
            // set to null
            socks5AuthMethodExchange = null;

            if (strictMode) {
                assert Logger.lowLevelDebug("strictMode is on, send socks5 connect here");
                sendSocks5Connect(ctx);
            }

            // make buffer for incoming data
            step = 5;
            byte[] connect5Bytes = new byte[5];
            // we only need 2 bytes to check whether connection established successfully
            // and read another THREE bytes to know how long this message is
            socks5ConnectResult = ByteArrayChannel.fromEmpty(connect5Bytes);
        }

        private void checkAndProcessFirst5BytesOfConnectResult(ConnectionHandlerContext ctx) {
            byte[] connect5Bytes = socks5ConnectResult.get();
            if (connect5Bytes[0] != 5 || connect5Bytes[1] != 0) {
                // version != 5 or resp != success
                Logger.error(LogType.INVALID_EXTERNAL_DATA,
                    "response version is wrong or resp is not success: " + connect5Bytes[0] + "," + connect5Bytes[1] +
                        ". handling " + domain + ":" + port);
                utilAlertFail(ctx);
                return;
            }
            // [2] is preserved, ignore that
            // check [3] for type
            int leftLen;
            switch (connect5Bytes[3]) {
                case 1: // ipv4
                    leftLen = 4 - 1 + 2;
                    break;
                case 3: // domain
                    leftLen = Utils.positive(connect5Bytes[4]) + 2;
                    break;
                case 4: // ipv6
                    leftLen = 16 - 1 + 2;
                    break;
                default:
                    Logger.error(LogType.INVALID_EXTERNAL_DATA,
                        "RESP_TYPE is invalid: " + connect5Bytes[3]);
                    utilAlertFail(ctx);
                    return;
            }

            // check the input buffer, whether already contain the left data
            if (ctx.connection.getInBuffer().used() == leftLen) {
                ctx.connection.getInBuffer().clear();
                done(ctx);
            } else if (ctx.connection.getInBuffer().used() < leftLen) {
                // read more data
                step = 6;
                byte[] foo = new byte[leftLen];
                socks5ConnectResult = ByteArrayChannel.fromEmpty(foo);
            } else {
                // more than leftLen, which is invalid
                Logger.error(LogType.INVALID_EXTERNAL_DATA,
                    "still got data after the connection response" + ctx.connection.getInBuffer().toString());
                utilAlertFail(ctx);
            }
        }

        private void done(ConnectionHandlerContext ctx) {
            assert Logger.lowLevelDebug("handshake operation done " + ctx.connection);

            socks5ConnectResult = null;
            // remove the connection from loop
            ctx.eventLoop.removeConnection(ctx.connection);

            // add respond to the proxy lib

            providedCallback.accept(new AlreadyConnectedConnector(
                ctx.connection.remote, (ClientConnection) ctx.connection, ctx.eventLoop
            ));

            // every thing is done now
        }

        @Override
        public void writable(ConnectionHandlerContext ctx) {
            // do nothing here, the out buffer is large enough
        }

        @Override
        public void exception(ConnectionHandlerContext ctx, IOException err) {
            Logger.error(LogType.CONN_ERROR, "connection " + ctx.connection + " got exception", err);
            utilAlertFail(ctx);
        }

        @Override
        public void closed(ConnectionHandlerContext ctx) {
            Logger.warn(LogType.CONN_ERROR, "connection " + ctx.connection + " closed, so the proxy cannot establish");
            utilAlertFail(ctx);
        }

        @Override
        public void removed(ConnectionHandlerContext ctx) {
            assert Logger.lowLevelDebug("connection " + ctx.connection + " removed");
        }
    }

    static class CommonProcess {
        static ClientConnection makeSSLConnection(SvrHandleConnector connector) throws IOException {
            return makeSSLConnection(null, connector);
        }

        static ClientConnection makeSSLConnection(SelectorEventLoop loop, SvrHandleConnector connector) throws IOException {
            SSLEngine engine;
            String hostname = connector.getHostName();
            if (hostname == null) {
                engine = WebSocksUtils.getSslContext().createSSLEngine();
            } else {
                engine = WebSocksUtils.getSslContext().createSSLEngine(hostname, connector.remote.getPort());
            }
            SSLParameters params = new SSLParameters();
            {
                params.setApplicationProtocols(new String[]{"http/1.1"});
                if (hostname != null) {
                    assert Logger.lowLevelDebug("using hostname " + hostname + " as sni");
                    params.setServerNames(Collections.singletonList(new SNIHostName(hostname)));
                }
            }
            engine.setUseClientMode(true);
            engine.setSSLParameters(params);

            SSLUtils.SSLBufferPair pair;
            if (loop == null) {
                assert Logger.lowLevelDebug("event loop not specified, so we ignore the resumer for the ssl buffer pair");
                pair = SSLUtils.genbuf(
                    engine,
                    RingBuffer.allocate(SSLUtils.PLAIN_TEXT_SIZE),
                    RingBuffer.allocate(16384),
                    32768,
                    32768);
            } else {
                pair = SSLUtils.genbuf(
                    engine,
                    RingBuffer.allocate(SSLUtils.PLAIN_TEXT_SIZE),
                    RingBuffer.allocate(16384),
                    32768,
                    32768,
                    loop);
            }
            return connector.connect(WebSocksUtils.getConnectionOpts(), pair.left, pair.right);
        }

        static void sendUpgrade(ClientConnectionHandlerContext ctx, String domainOfProxy, String user, String pass) {
            // send http upgrade on connection
            byte[] bytes = ("" +
                "GET / HTTP/1.1\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Host: " + domainOfProxy + "\r\n" +
                "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" + // copied from rfc 6455, we don't care in the protocol
                "Sec-WebSocket-Version: 13\r\n" +
                "Sec-WebSocket-Protocol: socks5\r\n" + // for now, we support socks5 only
                "Authorization: Basic " +
                Base64.getEncoder().encodeToString((user + ":" + WebSocksUtils.calcPass(pass, Utils.currentMinute())).getBytes()) +
                "\r\n" +
                "\r\n"
            ).getBytes();
            ByteArrayChannel chnl = ByteArrayChannel.fromFull(bytes);
            ctx.connection.getOutBuffer().storeBytesFrom(chnl);
            // the out-buffer is large enough to fit the message, so no need to buffer it from here
        }

        static void parseUpgradeResp(ConnectionHandlerContext ctx, HttpRespParser httpRespParser,
                                     Runnable failCallback, Runnable successCallback) {
            // http
            int res = httpRespParser.feed(ctx.connection.getInBuffer());
            if (res != 0) {
                String errMsg = httpRespParser.getErrorMessage();
                if (errMsg != null) {
                    failCallback.run();
                } // otherwise // want more data
                return;
            }

            HttpResp resp = httpRespParser.getResult();
            assert Logger.lowLevelDebug("got http response: " + resp);

            // status code should be 101
            if (!resp.statusCode.toString().trim().equals("101")) {
                // the server refused to upgrade to WebSocket
                failCallback.run();
                return;
            }
            // check headers
            if (!WebSocksUtils.checkUpgradeToWebSocketHeaders(resp.headers, true)) {
                failCallback.run();
                return;
            }

            // done
            successCallback.run();
        }
    }

    private final boolean strictMode;
    private final Map<String, List<DomainChecker>> proxyDomains;
    private final Map<String, ServerGroup> servers;
    private final String user;
    private final String pass;
    private final Map<String, ConnectionPool> pool;

    public WebSocksProxyAgentConnectorProvider(NetEventLoop eventLoop,
                                               ConfigProcessor config) {
        this.strictMode = config.isStrictMode();
        this.proxyDomains = config.getDomains();
        this.servers = config.getServers();
        this.user = config.getUser();
        this.pass = config.getPass();

        pool = new HashMap<>();
        for (String alias : config.getServers().keySet()) {
            final String finalAlias = alias;
            pool.put(alias, new ConnectionPool(eventLoop,
                cb -> new WebSocksPoolHandler(finalAlias, cb),
                config.getPoolSize()));
        }
    }

    private String getProxy(String address, int port) {
        for (Map.Entry<String, List<DomainChecker>> entry : proxyDomains.entrySet()) {
            for (DomainChecker checker : entry.getValue()) {
                if (checker.needProxy(address, port)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    @Override
    public void provide(Connection accepted, AddressType type, String address, int port, Consumer<Connector> providedCallback) {
        // check whether need to proxy to the WebSocks server
        String serverAlias = getProxy(address, port);
        if (serverAlias == null) {
            Logger.alert("directly request " + address + ":" + port);
            // just directly connect to the endpoint
            Utils.directConnect(type, address, port, providedCallback);
            return;
        }

        // proxy the net flow using WebSocks
        Logger.alert("proxy the request to " + address + ":" + port + " via " + serverAlias);

        NetEventLoop loop = accepted.getEventLoop();
        if (loop == null) {
            Logger.shouldNotHappen("the loop should be attached to the connection");
            providedCallback.accept(null);
            return;
        }

        // try to fetch an existing connection from pool
        pool.get(serverAlias).get(loop.getSelectorEventLoop(), conn -> {
            boolean isPooledConn = conn != null;
            if (conn == null) {
                // retrieve a remote connection
                SvrHandleConnector connector = servers.get(serverAlias).next(null/*we ignore the source because it's wrr*/);
                if (connector == null) {
                    // no connectors for now
                    // the process is definitely cannot proceed
                    // we do not try direct connect here
                    // (because it's specified in config file that this domain requires proxy)
                    // just raise error
                    providedCallback.accept(null);
                    return;
                }
                try {
                    if ((Boolean) connector.getData() /*useSSL, see ConfigProcessor*/) {
                        conn = CommonProcess.makeSSLConnection(loop.getSelectorEventLoop(), connector);
                    } else {
                        conn = connector.connect(
                            WebSocksUtils.getConnectionOpts(),
                            RingBuffer.allocateDirect(16384), RingBuffer.allocateDirect(16384));
                    }
                } catch (IOException e) {
                    Logger.error(LogType.CONN_ERROR, "connect to " + connector + " failed", e);
                    providedCallback.accept(null);
                    return;
                }
            }

            String hostname = conn.remote.getHostString();
            if (Utils.isIpLiteral(hostname)) {
                hostname = null;
            }
            try {
                loop.addClientConnection(conn, null, new AgentClientConnectionHandler(
                    hostname, type, address, port,
                    providedCallback, isPooledConn));
            } catch (IOException e) {
                Logger.error(LogType.EVENT_LOOP_ADD_FAIL, "add " + conn + " to loop failed", e);
                providedCallback.accept(null);
            }
        });
    }
}
