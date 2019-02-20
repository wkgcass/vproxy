package net.cassite.vproxyx.websocks5;

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
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class WebSocks5ProtocolHandler implements ProtocolHandler<Tuple<WebSocks5ProxyContext, Callback<Connector, IOException>>> {
    private final HttpProtocolHandler httpProtocolHandler = new HttpProtocolHandler(false) {
        @Override
        protected void request(ProtocolHandlerContext<HttpContext> ctx) {
            HttpReq req = ctx.data.result;
            if (!req.method.toString().equals("GET")) {
                fail(ctx, 400, "invalid http method for upgrading to WebSocket");
                return;
            }
            if (!WebSocks5Utils.checkUpgradeToWebSocketHeaders(req.headers, false)) {
                fail(ctx, 400, "upgrading related headers are invalid");
                return;
            }
            if (ctx.inBuffer.used() != 0) {
                fail(ctx, 400, "upgrading request should not contain a body");
                return;
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
            // everything is find, make an upgrade
            {
                // handling on the server side
                WebSocks5HttpContext wrapCtx = (WebSocks5HttpContext) ctx.data;
                wrapCtx.webSocks5ProxyContext.step = 2; // next step is WebSocket
                int expectingLen = WebSocks5Utils.bytesToSendForWebSocketFrame.length;
                byte[] foo = new byte[expectingLen];
                wrapCtx.webSocks5ProxyContext.webSocketBytes = ByteArrayChannel.fromEmpty(foo);
            }
            ctx.write(response(101, accept)); // respond to the client about the upgrading
        }

        private void fail(ProtocolHandlerContext<HttpContext> ctx, int code, String msg) {
            assert Logger.lowLevelDebug("WebSocket handshake failed: " + msg);
            ctx.inBuffer.clear();
            ctx.write(response(code, msg));
            // no need to tell the Proxy it fails
            // the client may make another http request
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
                sb.append("\r\n"); // end headers (and also the resp)
            } else {
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

    @Override
    public void init(ProtocolHandlerContext<Tuple<WebSocks5ProxyContext, Callback<Connector, IOException>>> ctx) {
        ctx.data = new Tuple<>(new WebSocks5ProxyContext(
            new ProtocolHandlerContext<>(ctx.connectionId, ctx.connection, ctx.loop, httpProtocolHandler),
            new ProtocolHandlerContext<>(ctx.connectionId, ctx.connection, ctx.loop, socks5Handler)
        ), null);
        ctx.data.left.step = 1;
        ctx.data.left.httpContext.data = new WebSocks5HttpContext(ctx.data.left);
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
    public void readable(ProtocolHandlerContext<Tuple<WebSocks5ProxyContext, Callback<Connector, IOException>>> ctx) {
        if (ctx.data.left.step == 1) { // http step
            httpProtocolHandler.readable(ctx.data.left.httpContext);
        } else if (ctx.data.left.step == 2) { // WebSocket
            handleWebSocket(ctx);
        } else { // socks5 step
            socks5Handler.readable(ctx.data.left.socks5Context);
        }
    }

    private void handleWebSocket(ProtocolHandlerContext<Tuple<WebSocks5ProxyContext, Callback<Connector, IOException>>> ctx) {
        ctx.inBuffer.writeTo(ctx.data.left.webSocketBytes);
        if (ctx.data.left.webSocketBytes.free() != 0) {
            return; // need more data
        }
        if (ctx.inBuffer.used() != 0) {
            assert Logger.lowLevelDebug("the client still have data to write after header of the WebSocket frame");
            ctx.data.right.failed(new IOException("protocol failed")); // callback with null
            // the connection will be closed by the Proxy lib
            return;
        }
        ctx.data.left.step = 3; // socks5
        WebSocks5Utils.sendWebSocketFrame(ctx.connection.getOutBuffer());
    }

    @Override
    public void exception(ProtocolHandlerContext<Tuple<WebSocks5ProxyContext, Callback<Connector, IOException>>> ctx, Throwable err) {
        // connection should be closed by the protocol lib
        // we ignore the exception here
        assert Logger.lowLevelDebug("WebSocks5 exception " + ctx.connectionId + ", " + err);
    }

    @Override
    public void end(ProtocolHandlerContext<Tuple<WebSocks5ProxyContext, Callback<Connector, IOException>>> ctx) {
        // connection is closed by the protocol lib
        // we ignore the event here
        assert Logger.lowLevelDebug("WebSocks5 end " + ctx.connectionId);
    }

    @Override
    public boolean closeOnRemoval(ProtocolHandlerContext<Tuple<WebSocks5ProxyContext, Callback<Connector, IOException>>> ctx) {
        if (ctx.data.left.step == 1 || ctx.data.left.step == 2) { // http step or WebSocket frame step
            return true; // proxy not established yet, so close the connection
        } else { // socks5 step
            return socks5Handler.closeOnRemoval(ctx.data.left.socks5Context);
        }
    }
}
