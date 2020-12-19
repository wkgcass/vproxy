package vproxyx.websocks;

import vfd.IP;
import vjson.JSON;
import vjson.util.ObjectBuilder;
import vproxy.socks.Socks5ProxyProtocolHandler;
import vproxy.util.CoreUtils;
import vproxybase.connection.ConnectableConnection;
import vproxybase.connection.ConnectionOpts;
import vproxybase.connection.Connector;
import vproxybase.dns.Resolver;
import vproxybase.http.HttpContext;
import vproxybase.http.HttpProtocolHandler;
import vproxybase.processor.http1.entity.Header;
import vproxybase.processor.http1.entity.Request;
import vproxybase.processor.http1.entity.Response;
import vproxybase.protocol.ProtocolHandler;
import vproxybase.protocol.ProtocolHandlerContext;
import vproxybase.protocol.SubProtocolHandlerContext;
import vproxybase.selector.wrap.file.FileFD;
import vproxybase.util.*;
import vproxybase.util.nio.ByteArrayChannel;
import vproxybase.util.ringbuffer.ByteBufferRingBuffer;
import vproxybase.util.ringbuffer.SSLUtils;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Supplier;

public class WebSocksProtocolHandler implements ProtocolHandler<Tuple<WebSocksProxyContext, Callback<Connector, IOException>>> {
    private interface Tool {
        String prefix();

        void handle(WebSocksHttpProtocolHandler handler, ProtocolHandlerContext<HttpContext> ctx, String argument);
    }

    // some utils tools provided by the websocks-proxy-server through http
    private static final String TOOLS_PREFIX = "/tools";
    private static final Tool[] tools = new Tool[]{
        new ResolveTool(),
    };

    static class ResolveTool implements Tool {
        @Override
        public String prefix() {
            return "/resolve?domain=";
        }

        @Override
        public void handle(WebSocksHttpProtocolHandler handler, ProtocolHandlerContext<HttpContext> ctx, String argument) {
            Resolver.getDefault().resolve(argument, new Callback<>() {
                @Override
                protected void onSucceeded(IP value) {
                    JSON.Object object = new ObjectBuilder()
                        .put("domain", argument)
                        .putArray("addresses", arr -> arr.add(value.formatToIPString())).build();
                    ctx.write(handler.response(object));
                }

                @Override
                protected void onFailed(UnknownHostException err) {
                    handler.fail(ctx, 400, "Cannot resolve host: " + Utils.formatErr(err));
                }
            });
        }
    }

    class WebSocksHttpProtocolHandler extends HttpProtocolHandler {
        private final Supplier<ProtocolHandlerContext<Tuple<WebSocksProxyContext, Callback<Connector, IOException>>>> rootCtxGetter;

        protected WebSocksHttpProtocolHandler(Supplier<ProtocolHandlerContext<Tuple<WebSocksProxyContext, Callback<Connector, IOException>>>> rootCtxGetter) {
            super(false);
            this.rootCtxGetter = rootCtxGetter;
        }

        @Override
        protected void request(ProtocolHandlerContext<HttpContext> ctx) {
            Request req = ctx.data.result;
            assert Logger.lowLevelDebug("receive new request " + req);
            if (!req.method.equals("GET")) {
                fail(ctx, 400, "unsupported http method " + req.method);
                return;
            }
            if (req.headers.stream().map(h -> h.key).noneMatch(key -> key.equalsIgnoreCase("upgrade"))) {
                assert Logger.lowLevelDebug("not upgrade request, try to respond with a registered page");
                if (pageProvider == null) {
                    assert Logger.lowLevelDebug("pageProvider is not set, cannot respond a page");
                    // fall through
                } else {
                    if (req.uri.contains("..")) {
                        Logger.warn(LogType.INVALID_EXTERNAL_DATA, "the request " + req.uri + " wants to get upper level resources, which might be an attack");
                        // fall through
                    } else {
                        String uri = req.uri;
                        // check for tools
                        for (Tool tool : tools) {
                            String prefix = TOOLS_PREFIX + tool.prefix();
                            if (!uri.startsWith(prefix)) {
                                continue;
                            }
                            String arg = uri.substring(prefix.length()).trim();
                            if (arg.isEmpty()) {
                                fail(ctx, 400, "Invalid argument");
                                return;
                            }
                            tool.handle(this, ctx, arg);
                            return;
                        }
                        if (uri.contains("?")) {
                            uri = uri.substring(0, uri.indexOf("?"));
                        }
                        Logger.alert("incoming request " + ctx.connection.remote + "->" + uri);
                        fail(ctx, 200, uri);
                        return;
                    }
                }
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
            for (Header header : req.headers) {
                if (header.key.trim().equalsIgnoreCase("sec-websocket-key")) {
                    key = header.value.trim();
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
            response(101, accept, ctx); // respond to the client about the upgrading
        }

        // it's ordered with `-priority`, smaller the index is, higher priority it has
        private final List<String> supportedProtocols = Collections.singletonList("socks5");

        private String selectProtocol(List<Header> headers) {
            List<String> protocols = new ArrayList<>();
            for (Header h : headers) {
                if (h.key.trim().equalsIgnoreCase("sec-websocket-protocol")) {
                    protocols.add(h.value.trim());
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
            response(code, msg, ctx);
            // no need to tell the Proxy it fails
            // the client may make another http request
        }

        private boolean checkAuth(List<Header> headers) {
            for (Header h : headers) {
                if (h.key.trim().equalsIgnoreCase("authorization")) {
                    String v = h.value.trim();
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
            long m = Utils.currentMinute();
            long mInc = m + 60_000;
            long mDec = m - 60_000;
            return pass.equals(WebSocksUtils.calcPass(expected, m))
                || pass.equals(WebSocksUtils.calcPass(expected, mInc))
                || pass.equals(WebSocksUtils.calcPass(expected, mDec));
        }

        private static final String rfc6455UUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

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

        private byte[] response(JSON.Instance body) {
            String statusMsg = HttpStatusCodeReasonMap.get(200);
            Response resp = new Response();
            resp.version = "HTTP/1.1";
            resp.statusCode = 200;
            resp.reason = statusMsg;
            resp.headers = new LinkedList<>();
            resp.headers.add(new Header("Server", "vproxy/" + Version.VERSION));
            resp.headers.add(new Header("Date", new Date().toString()));
            resp.headers.add(new Header("Content-Type", "application/json"));
            if (body != null) {
                ByteArray foo = ByteArray.from(body.pretty().getBytes());
                resp.headers.add(new Header("Content-Length", "" + foo.length()));
                resp.body = foo;
            }
            return resp.toByteArray().toJavaArray();
        }

        private void response(int statusCode, String msg, ProtocolHandlerContext<HttpContext> ctx) {
            // check whether the page exists when statusCode is 200
            PageProvider.PageResult page = null;
            if (statusCode == 200) {
                page = pageProvider.getPage(msg);
                if (page == null) {
                    statusCode = 404;
                    msg = msg + " not found";
                } else if (page.redirect != null) {
                    statusCode = 302;
                    msg = page.redirect;
                }
            }

            String statusMsg = HttpStatusCodeReasonMap.get(statusCode);
            Response resp = new Response();
            resp.version = "HTTP/1.1";
            resp.statusCode = statusCode;
            resp.reason = statusMsg;
            resp.headers = new LinkedList<>();
            resp.headers.add(new Header("Server", "vproxy/" + Version.VERSION));
            resp.headers.add(new Header("Date", new Date().toString()));
            FileFD fileToSend = null;
            if (statusCode == 101) {
                resp.headers.add(new Header("Upgrade", "websocket"));
                resp.headers.add(new Header("Connection", "Upgrade"));
                resp.headers.add(new Header("Sec-Websocket-Accept", msg));
                resp.headers.add(new Header("Sec-WebSocket-Protocol", "socks5"));
            } else if (statusCode == 200) {
                resp.headers.add(new Header("Content-Type", page.mime));
                assert page.content != null || page.file != null;
                assert page.content == null || page.file == null;
                if (page.file == null) {
                    resp.headers.add(new Header("Content-Length", "" + page.content.length()));
                    resp.body = page.content;
                } else {
                    resp.headers.add(new Header("Content-Length", "" + page.file.length()));
                    resp.headers.add(new Header("Connection", "Close"));
                    fileToSend = page.file;
                }
                if (page.cacheAge != 0L) {
                    resp.headers.add(new Header("Cache-Control", "max-age=" + page.cacheAge));
                }
            } else if (statusCode == 302) {
                ByteArray body = ByteArray.from(("" +
                    "<html>\r\n" +
                    "<head><title>302 Found</title></head>\r\n" +
                    "<body bgcolor=\"white\">\r\n" +
                    "<center><h1>302 Found</h1></center>\r\n" +
                    "<hr><center>vproxy/" + Version.VERSION + "</center>\r\n" +
                    "</body>\r\n" +
                    "</html>\r\n").getBytes());
                resp.headers.add(new Header("Location", msg));
                resp.headers.add(new Header("Content-Length", "" + body.length()));
                resp.body = body;
            } else {
                if (statusCode == 401) {
                    resp.headers.add(new Header("WWW-Authenticate", "Basic"));
                }
                resp.headers.add(new Header("Content-Type", "text/html"));
                {
                    String rawIp = ctx.connection.remote.getAddress().formatToIPString();
                    String xff = null;
                    if (ctx.data != null && ctx.data.result != null && ctx.data.result.headers != null) {
                        var headers = ctx.data.result.headers;
                        for (var header : headers) {
                            if (header.key.trim().toLowerCase().equals("x-forwarded-for")) {
                                xff = header.value.trim();
                                break;
                            }
                        }
                    }
                    if (xff == null) {
                        xff = "(none)";
                    }
                    msg = ErrorPages.build(statusCode, statusMsg, msg, rawIp, xff);
                }
                ByteArray body = ByteArray.from(msg.getBytes());
                resp.headers.add(new Header("Content-Length", "" + body.length()));
                resp.body = body;
            }
            ctx.write(resp.toByteArray().toJavaArray());
            if (fileToSend != null) {
                final FileFD file = fileToSend;
                // the header is small, it should be directly flushed
                // make sure this operation happen after the call of writing the header
                ctx.loop.getSelectorEventLoop().runOnLoop(() -> ctx.loop.getSelectorEventLoop().runOnLoop(() -> {
                    var rootCtx = rootCtxGetter.get();
                    var cb = rootCtx.data.right;
                    if (cb == null) {
                        Logger.shouldNotHappen("the callback Callback<Connector, IOException> is null while trying to send file " + file);
                        return;
                    }
                    // make a connector
                    var conn = ConnectableConnection.wrap(file, file.getRemoteAddress(), ConnectionOpts.getDefault(),
                        RingBuffer.allocate(0), RingBuffer.allocateDirect(0));
                    var connector = new AlreadyConnectedConnector(file.getRemoteAddress(), conn, ctx.loop);
                    String id = ctx.connection.remote + " <- " + connector.remote;
                    Logger.alert("sending large file: " + id);
                    rootCtx.data.left.step = 4; // large file
                    cb.succeeded(connector);
                }));
            }
        }
    }

    private final Socks5ProxyProtocolHandler socks5Handler = new Socks5ProxyProtocolHandler(
        (accepted, type, address, port, providedCallback) ->
            CoreUtils.directConnect(type, address, port, providedCallback));

    private final Map<String, String> auth;
    private final Supplier<SSLEngine> engineSupplier;
    private final PageProvider pageProvider;

    public WebSocksProtocolHandler(Map<String, String> auth, Supplier<SSLEngine> engineSupplier, PageProvider pageProvider) {
        this.auth = auth;
        this.engineSupplier = engineSupplier;
        this.pageProvider = pageProvider;
    }

    private void initSSL(ProtocolHandlerContext<Tuple<WebSocksProxyContext, Callback<Connector, IOException>>> ctx) {
        if (engineSupplier == null) {
            return; // not ssl, no need to init
        }
        assert Logger.lowLevelDebug("should upgrade the connection to ssl");
        SSLEngine engine = engineSupplier.get();
        SSLUtils.SSLBufferPair pair = SSLUtils.genbuf(engine,
            // we can allocate new buffer here, but it's ok to use the original allocated buffers
            (ByteBufferRingBuffer) ctx.connection.getInBuffer(),
            (ByteBufferRingBuffer) ctx.connection.getOutBuffer(),
            ctx.connection.channel);
        try {
            // when init, there should have not read any data yet
            // so we should safely replace the buffers
            ctx.connection.UNSAFE_replaceBuffer(pair.left, pair.right, true);
        } catch (IOException e) {
            Logger.shouldNotHappen("got error when switching buffers", e);
            // raise error to let others handle the error
            throw new RuntimeException("should not happen and the error is unrecoverable", e);
        }
    }

    @Override
    public void init(ProtocolHandlerContext<Tuple<WebSocksProxyContext, Callback<Connector, IOException>>> ctx) {
        initSSL(ctx); // init if it's ssl (or may simply do nothing if not ssl)
        ctx.data = new Tuple<>(new WebSocksProxyContext(
            new SubProtocolHandlerContext<>(ctx, ctx.connectionId, ctx.connection, ctx.loop, new WebSocksHttpProtocolHandler(() -> ctx)),
            new SubProtocolHandlerContext<>(ctx, ctx.connectionId, ctx.connection, ctx.loop, socks5Handler)
        ), null);
        ctx.data.left.step = 1; // http
        ctx.data.left.httpContext.data = new WebSocksHttpContext(ctx.data.left);
        socks5Handler.init(ctx.data.left.socks5Context);
        ctx.data.left.socks5Context.data = new Tuple<>(
            ctx.data.left.socks5Context.data.left,
            // simply proxy the values of the Proxy lib
            new Callback<>() {
                @Override
                protected void onSucceeded(Connector connector) {
                    String id = ctx.connection.remote + "->" + connector.remote;
                    Logger.alert("proxy establishes: " + id);
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
            ctx.data.left.httpContext.handler.readable(ctx.data.left.httpContext);
        } else if (ctx.data.left.step == 2) { // WebSocket
            handleWebSocket(ctx);
        } else { // socks5 step
            socks5Handler.readable(ctx.data.left.socks5Context);
        }
    }

    private void handleWebSocket(ProtocolHandlerContext<Tuple<WebSocksProxyContext, Callback<Connector, IOException>>> ctx) {
        ByteArrayChannel chnl = ctx.data.left.webSocketBytes;
        ctx.inBuffer.writeTo(chnl);
        if (chnl.used() > 2) {
            // check first 2 bytes, whether it's indicating that it's a PONG message
            byte[] b = chnl.getBytes();
            if ((b[0] & 0xf) == 0xA) {
                // opcode is PONG
                assert Logger.lowLevelDebug("received PONG message");
                if (b[0] != (byte) 0b10001010 || b[1] != 0) {
                    Logger.error(LogType.INVALID_EXTERNAL_DATA, "the message is PONG, but is not expected: [" + b[0] + "][" + b[1] + "]");
                    // here we went to an undefined state, we just leave it here
                }
                Utils.shiftLeft(b, 2);
                ctx.data.left.webSocketBytes = ByteArrayChannel.from(b, 0, chnl.used() - 2, b.length + 2 - chnl.used());

                // then we read again
                handleWebSocket(ctx);
                return;
            } // otherwise it's not a PONG packet
        } else {
            return; // need more data
        }
        if (ctx.data.left.webSocketBytes.free() != 0) {
            return; // need more data
        }
        assert Logger.lowLevelDebug("web socket data receiving done");
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
        } else if (ctx.data.left.step == 3) { // socks5 step
            return socks5Handler.closeOnRemoval(ctx.data.left.socks5Context);
        } else { // large file
            return false;
        }
    }
}
