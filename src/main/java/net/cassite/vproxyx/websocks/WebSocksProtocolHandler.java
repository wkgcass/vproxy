package net.cassite.vproxyx.websocks;

import net.cassite.vproxy.connection.Connector;
import net.cassite.vproxy.http.HttpContext;
import net.cassite.vproxy.http.HttpHeader;
import net.cassite.vproxy.http.HttpProtocolHandler;
import net.cassite.vproxy.http.HttpReq;
import net.cassite.vproxy.protocol.ProtocolHandler;
import net.cassite.vproxy.protocol.ProtocolHandlerContext;
import net.cassite.vproxy.socks.Socks5ProxyProtocolHandler;
import net.cassite.vproxy.util.*;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class WebSocksProtocolHandler implements ProtocolHandler<Tuple<WebSocksProxyContext, Callback<Connector, IOException>>> {
    private final HttpProtocolHandler httpProtocolHandler = new HttpProtocolHandler(false) {
        @Override
        protected void request(ProtocolHandlerContext<HttpContext> ctx) {
            HttpReq req = ctx.data.result;
            assert Logger.lowLevelDebug("receive new request " + req);
            if (!req.method.toString().equals("GET")) {
                fail(ctx, 400, "invalid http method for upgrading to WebSocket");
                return;
            }
            if (!WebSocksUtils.checkUpgradeToWebSocketHeaders(req.headers, false)) {
                fail(ctx, 400, "upgrading related headers are invalid");
                return;
            }
            if (ctx.inBuffer.used() != 0) {
                fail(ctx, 400, "upgrading request should not contain a body");
                return;
            }
            if (!checkAuth(req.headers)) {
                fail(ctx, 401, "auth failed");
                return;
            }
            {
                String useProtocol = selectProtocol(req.headers);
                if (useProtocol == null) {
                    fail(ctx, 400, "no supported protocol");
                    return;
                }
                // we only support socks5 for now, so no need to save this protocol string
            }

            String key = null;
            for (HttpHeader header : req.headers) {
                if (header.key.toString().trim().equalsIgnoreCase("sec-websocket-key")) {
                    key = header.value.toString().trim();
                }
            }
            assert key != null;
            String accept = getWebSocketAccept(key);
            if (accept == null) {
                fail(ctx, 500, "generating sec-websocket-accept failed");
                return;
            }
            // everything is fine, make an upgrade
            {
                // handling on the server side
                WebSocksHttpContext wrapCtx = (WebSocksHttpContext) ctx.data;
                wrapCtx.webSocksProxyContext.step = 2; // next step is WebSocket
                int expectingLen = WebSocksUtils.bytesToSendForWebSocketFrame.length;
                byte[] foo = new byte[expectingLen];
                wrapCtx.webSocksProxyContext.webSocketBytes = ByteArrayChannel.fromEmpty(foo);
            }
            ctx.write(response(101, accept)); // respond to the client about the upgrading
        }

        // it's ordered with `-priority`, smaller the index is, higher priority it has
        private final List<String> supportedProtocols = Collections.singletonList("socks5");

        private String selectProtocol(List<HttpHeader> headers) {
            List<String> protocols = new ArrayList<>();
            for (HttpHeader h : headers) {
                if (h.key.toString().trim().equalsIgnoreCase("sec-websocket-protocol")) {
                    protocols.add(h.value.toString().trim());
                }
            }
            if (protocols.isEmpty())
                return null;

            for (String p : supportedProtocols) {
                if (protocols.contains(p))
                    return p;
            }
            return null;
        }

        private void fail(ProtocolHandlerContext<HttpContext> ctx, int code, String msg) {
            assert Logger.lowLevelDebug("WebSocket handshake failed: " + msg);
            ctx.inBuffer.clear();
            ctx.write(response(code, msg));
            // no need to tell the Proxy it fails
            // the client may make another http request
        }

        private boolean checkAuth(List<HttpHeader> headers) {
            for (HttpHeader h : headers) {
                if (h.key.toString().trim().equalsIgnoreCase("authorization")) {
                    String v = h.value.toString().trim();
                    if (!v.startsWith("Basic ")) {
                        assert Logger.lowLevelDebug("Authorization header not Basic: " + v);
                        return false;
                    }
                    v = v.substring("Basic ".length()).trim();
                    try {
                        v = new String(Base64.getDecoder().decode(v.getBytes()));
                    } catch (IllegalArgumentException e) {
                        assert Logger.lowLevelDebug("Authorization Basic not base64: " + v);
                        return false;
                    }
                    String[] userpass = v.split(":");
                    if (userpass.length != 2) {
                        assert Logger.lowLevelDebug("not user:pass, " + v);
                        return false;
                    }
                    String user = userpass[0].trim();
                    String pass = userpass[1].trim();

                    String expectedPass = auth.get(user);
                    if (expectedPass == null) {
                        assert Logger.lowLevelDebug("user " + user + " not in registry");
                        return false;
                    }
                    assert Logger.lowLevelDebug("do check for " + user + ":" + pass);
                    return checkPass(pass, expectedPass);
                }
            }
            assert Logger.lowLevelDebug("no Authorization header");
            return false;
        }

        // we calculate the user's password with current minute number and sha256
        // the input should match any of the following:
        // 1. base64str(base64str(sha256(password)) + str(current_minute_dec_digital)))
        // 2. base64str(base64str(sha256(password)) + str(current_minute_dec_digital - 1)))
        // 3. base64str(base64str(sha256(password)) + str(current_minute_dec_digital + 1)))
        private boolean checkPass(String pass, String expected) {
            int m = Utils.currentMinute();
            int mInc = m + 1;
            if (mInc == 60)
                mInc = 0;
            int mDec = m - 1;
            if (mDec == -1)
                mDec = 59;
            return pass.equals(WebSocksUtils.calcPass(expected, m))
                || pass.equals(WebSocksUtils.calcPass(expected, mInc))
                || pass.equals(WebSocksUtils.calcPass(expected, mDec));
        }

        private final String rfc6455UUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

        private String getWebSocketAccept(String key) {
            String foo = key + rfc6455UUID;
            MessageDigest sha1;
            try {
                sha1 = MessageDigest.getInstance("SHA-1");
            } catch (NoSuchAlgorithmException e) {
                Logger.shouldNotHappen("no SHA-1 alg", e);
                return null;
            }
            sha1.update(foo.getBytes());
            return Base64.getEncoder().encodeToString(sha1.digest());
        }

        private final Map<Integer, String> STATUS_MSG = new HashMap<Integer, String>() {{
            put(101, "Switching Protocols");
            put(400, "Bad Request");
            put(401, "Unauthorized");
            put(500, "Internal Server Error");
        }};

        private byte[] response(int statusCode, String msg) {
            String statusMsg = STATUS_MSG.get(statusCode);
            StringBuilder sb = new StringBuilder();
            sb.append("HTTP/1.1 ").append(statusCode).append(" ").append(statusMsg).append("\r\n");
            if (statusCode == 101) {
                sb.append("Upgrade: websocket\r\n");
                sb.append("Connection: Upgrade\r\n");
                sb.append("Sec-Websocket-Accept: ").append(msg).append("\r\n");
                sb.append("Sec-WebSocket-Protocol: socks5\r\n"); // for now we only support socks5
                sb.append("\r\n"); // end headers (and also the resp)
            } else {
                if (statusCode == 401) {
                    sb.append("WWW-Authenticate: Basic\r\n");
                }
                sb.append("Content-Length: ").append(msg.getBytes().length).append("\r\n");
                sb.append("\r\n"); // end headers
                sb.append(msg);
            }
            return sb.toString().getBytes();
        }
    };

    private final Socks5ProxyProtocolHandler socks5Handler = new Socks5ProxyProtocolHandler(
        (accepted, type, address, port, providedCallback) ->
            Utils.directConnect(type, address, port, providedCallback));

    private final Map<String, String> auth;

    public WebSocksProtocolHandler(Map<String, String> auth) {
        this.auth = auth;
    }

    @Override
    public void init(ProtocolHandlerContext<Tuple<WebSocksProxyContext, Callback<Connector, IOException>>> ctx) {
        ctx.data = new Tuple<>(new WebSocksProxyContext(
            new ProtocolHandlerContext<>(ctx.connectionId, ctx.connection, ctx.loop, httpProtocolHandler),
            new ProtocolHandlerContext<>(ctx.connectionId, ctx.connection, ctx.loop, socks5Handler)
        ), null);
        ctx.data.left.step = 1;
        ctx.data.left.httpContext.data = new WebSocksHttpContext(ctx.data.left);
        socks5Handler.init(ctx.data.left.socks5Context);
        ctx.data.left.socks5Context.data = new Tuple<>(
            ctx.data.left.socks5Context.data.left,
            // simply proxy the values of the Proxy lib
            new Callback<Connector, IOException>() {
                @Override
                protected void onSucceeded(Connector connector) {
                    ctx.data.right.succeeded(connector);
                }

                @Override
                protected void onFailed(IOException err) {
                    ctx.data.right.failed(err);
                }
            }
        );
    }

    @Override
    public void readable(ProtocolHandlerContext<Tuple<WebSocksProxyContext, Callback<Connector, IOException>>> ctx) {
        if (ctx.data.left.step == 1) { // http step
            httpProtocolHandler.readable(ctx.data.left.httpContext);
        } else if (ctx.data.left.step == 2) { // WebSocket
            handleWebSocket(ctx);
        } else { // socks5 step
            socks5Handler.readable(ctx.data.left.socks5Context);
        }
    }

    private void handleWebSocket(ProtocolHandlerContext<Tuple<WebSocksProxyContext, Callback<Connector, IOException>>> ctx) {
        ctx.inBuffer.writeTo(ctx.data.left.webSocketBytes);
        if (ctx.data.left.webSocketBytes.free() != 0) {
            return; // need more data
        }
        ctx.data.left.step = 3; // socks5
        WebSocksUtils.sendWebSocketFrame(ctx.connection.getOutBuffer());
        if (ctx.inBuffer.used() != 0) {
            // still have data
            // let's call readable to handle the socks step
            readable(ctx);
        }
    }

    @Override
    public void exception(ProtocolHandlerContext<Tuple<WebSocksProxyContext, Callback<Connector, IOException>>> ctx, Throwable err) {
        // connection should be closed by the protocol lib
        // we ignore the exception here
        assert Logger.lowLevelDebug("WebSocks exception " + ctx.connectionId + ", " + err);
    }

    @Override
    public void end(ProtocolHandlerContext<Tuple<WebSocksProxyContext, Callback<Connector, IOException>>> ctx) {
        // connection is closed by the protocol lib
        // we ignore the event here
        assert Logger.lowLevelDebug("WebSocks end " + ctx.connectionId);
    }

    @Override
    public boolean closeOnRemoval(ProtocolHandlerContext<Tuple<WebSocksProxyContext, Callback<Connector, IOException>>> ctx) {
        if (ctx.data.left.step == 1 || ctx.data.left.step == 2) { // http step or WebSocket frame step
            return true; // proxy not established yet, so close the connection
        } else { // socks5 step
            return socks5Handler.closeOnRemoval(ctx.data.left.socks5Context);
        }
    }
}
