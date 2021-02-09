package vserver.impl;

import vjson.JSON;
import vproxybase.connection.NetEventLoop;
import vproxybase.connection.ServerSock;
import vproxybase.http.HttpContext;
import vproxybase.http.HttpProtocolHandler;
import vproxybase.processor.http1.entity.Chunk;
import vproxybase.processor.http1.entity.Header;
import vproxybase.processor.http1.entity.Request;
import vproxybase.processor.http1.entity.Response;
import vproxybase.protocol.ProtocolHandlerContext;
import vproxybase.protocol.ProtocolServerConfig;
import vproxybase.protocol.ProtocolServerHandler;
import vproxybase.util.*;
import vserver.*;
import vserver.route.WildcardSubPath;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static vserver.HttpMethod.ALL_METHODS;

public class Http1ServerImpl extends AbstractServer implements HttpServer {
    private boolean started = false;
    private final Map<HttpMethod, Tree<SubPath, RoutingHandler>> routes = new HashMap<>(HttpMethod.values().length) {{
        for (HttpMethod m : HttpMethod.values()) {
            put(m, new Tree<>());
        }
    }};

    public Http1ServerImpl() {
    }

    public Http1ServerImpl(NetEventLoop loop) {
        super(loop);
    }

    @Override
    protected String threadname() {
        return "http-1-server";
    }

    private void record(Tree<SubPath, RoutingHandler> tree, SubPath subpath, RoutingHandler handler) {
        // null means this sub-path is '/' which is the end of this route
        if (subpath == null) {
            tree.leaf(handler);
            return;
        }
        // check the last branch
        // note: here we only check the LAST branch because we need to preserve the handling order
        // consider this situation:
        // 1. handle(..., /a/b,  ...) registers /a/b
        // 2. handle(..., /a/:x, ...) registers /a/:x
        // 3. handle(..., /a/b,  ...) registers /a/b
        // we must NOT add the 3rd path to the branch of the 1st path because if so we will get order 1st,3rd,2nd
        // instead of the expected order 1st,2nd,3rd
        var last = tree.lastBranch();
        if (last != null && last.data.currentSame(subpath)) {
            // can use the last node
            record(last, subpath.next(), handler);
            return;
        }
        // must be new route
        var br = tree.branch(subpath);
        record(br, subpath.next(), handler);
    }

    private void record(HttpMethod[] methods, SubPath route, RoutingHandler handler) {
        for (HttpMethod m : methods) {
            record(routes.get(m), route, handler);
        }
    }

    @Override
    public HttpServer handle(HttpMethod[] methods, SubPath route, RoutingHandler handler) {
        if (started) {
            throw new IllegalStateException("This http server is already started");
        }
        record(methods, route, handler);
        return this;
    }

    private void preListen() {
        if (started) {
            throw new IllegalStateException("This http server is already started");
        }
        started = true;
        record(ALL_METHODS, SubPath.create("/*"), this::handle404);
    }

    public void listen(ServerSock server) throws IOException {
        if (loop == null) {
            throw new IllegalStateException("loop not specified but listen(ServerSock) is called");
        }

        preListen();

        ProtocolServerHandler.apply(loop, server,
            new ProtocolServerConfig().setInBufferSize(4096).setOutBufferSize(4096),
            new HttpProtocolHandler(true) {
                @Override
                protected void request(ProtocolHandlerContext<HttpContext> ctx) {
                    handle(ctx);
                }
            });
    }

    private void handle404(RoutingContext ctx) {
        ctx.response()
            .status(404)
            .end(ByteArray.from(("Cannot " + ctx.method() + " " + ctx.uri() + "\r\n").getBytes()));
    }

    private void sendResponse(ProtocolHandlerContext<HttpContext> _pctx, Response response) {
        if (response.headers == null) {
            response.headers = new ArrayList<>(2);
            // may add
            // x-powered-by
            // content-length
        }
        boolean needAddServerId = true;
        for (Header h : response.headers) {
            if (h.key.equalsIgnoreCase("x-powered-by")) {
                needAddServerId = false;
                break;
            }
        }
        if (needAddServerId) {
            response.headers.add(new Header("X-Powered-By", "vproxy/" + Version.VERSION));
        }
        // fill in default values
        if (response.version == null) {
            response.version = "HTTP/1.1";
        }
        if (response.statusCode == 0) {
            response.statusCode = 200;
            response.reason = "OK";
        }
        boolean chunked = false;
        for (Header h : response.headers) {
            if (h.key.equalsIgnoreCase("transfer-encoding")) {
                if (h.value.equalsIgnoreCase("chunked")) {
                    chunked = true;
                    break;
                }
            }
        }
        if (!chunked) {
            if (response.body == null) {
                response.headers.add(new Header("Content-Length", "0"));
            } else {
                response.headers.add(new Header("Content-Length", Integer.toString(response.body.length())));
            }
        }
        _pctx.write(response.toByteArray().toJavaArray());
    }

    private void handle(ProtocolHandlerContext<HttpContext> _pctx) {
        Request request = _pctx.data.result;
        RoutingContext[] ctx = new RoutingContext[1];
        {
            final HttpMethod method;
            final Map<String, String> headers = new HashMap<>();
            final String uri = unescape(request.uri);
            final Map<String, String> query = new HashMap<>();
            final ByteArray body;
            final HttpResponse response;
            final HandlerChain chain;

            final List<String> paths;
            { // paths and query
                if (uri == null) {
                    Response resp = new Response();
                    resp.statusCode = 400;
                    resp.reason = "Bad Request";
                    resp.body = ByteArray.from("Bad Request: invalid uri\r\n".getBytes());
                    sendResponse(_pctx, resp);
                    return;
                }
                String path = uri;
                String queryPart = "";
                if (path.contains("?")) {
                    queryPart = path.substring(path.indexOf('?') + 1);
                    path = path.substring(0, path.indexOf('?'));
                }
                paths = Arrays.stream(path.split("/")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
                var queryList = Arrays.stream(queryPart.split("&")).filter(s -> !s.isBlank()).collect(Collectors.toList());
                for (var qkv : queryList) {
                    if (qkv.indexOf('=') == -1) {
                        query.put(qkv, "");
                    } else {
                        int idx = qkv.indexOf('=');
                        query.put(qkv.substring(0, idx), qkv.substring(idx + 1));
                    }
                }
            }

            { // method
                try {
                    method = HttpMethod.valueOf(request.method);
                } catch (RuntimeException e) {
                    Response resp = new Response();
                    resp.statusCode = 400;
                    resp.reason = "Bad Request";
                    resp.body = ByteArray.from("Bad Request: invalid method\r\n".getBytes());
                    sendResponse(_pctx, resp);
                    return;
                }
            }
            { // uri
                if (!uri.startsWith("/")) {
                    Response resp = new Response();
                    resp.statusCode = 400;
                    resp.reason = "Bad Request";
                    resp.body = ByteArray.from("Bad Request: invalid uri\r\n".getBytes());
                    sendResponse(_pctx, resp);
                    return;
                }
            }
            { // headers
                if (request.headers != null) {
                    for (Header h : request.headers) {
                        headers.put(h.key.toLowerCase(), h.value);
                    }
                }
                if (request.trailers != null) {
                    for (Header h : request.trailers) {
                        headers.put(h.key.toLowerCase(), h.value);
                    }
                }
            }
            { // body
                if (request.body != null) {
                    body = request.body;
                } else if (request.chunks != null) {
                    ByteArray foo = null;
                    for (Chunk c : request.chunks) {
                        if (foo == null) {
                            foo = c.content;
                        } else {
                            foo = foo.concat(c.content);
                        }
                    }
                    body = foo;
                } else {
                    body = null;
                }
            }
            { // response
                response = new HttpResponse() {
                    final Response response = new Response();
                    boolean isEnd = false;
                    boolean headersSent = false;
                    boolean allowChunked = false;

                    @Override
                    public HttpResponse status(int code, String msg) {
                        if (isEnd) {
                            throw new IllegalStateException("This response is already ended");
                        }
                        if (headersSent) {
                            throw new IllegalStateException("Headers of this response is already sent");
                        }
                        response.statusCode = code;
                        response.reason = msg;
                        return this;
                    }

                    @Override
                    public HttpResponse header(String key, String value) {
                        if (isEnd) {
                            throw new IllegalStateException("This response is already ended");
                        }
                        if (headersSent) {
                            throw new IllegalStateException("Headers of this response is already sent");
                        }
                        if (response.headers == null) {
                            response.headers = new LinkedList<>();
                        }
                        response.headers.add(new Header(key, value));
                        return this;
                    }

                    @Override
                    public HttpResponse sendHeadersWithChunked() {
                        if (headersSent) {
                            throw new IllegalStateException("Headers of this response is already sent");
                        }
                        if (response.headers == null) {
                            response.headers = new LinkedList<>();
                        }
                        boolean hasTransferEncoding = false;
                        String transferEncoding = "chunked";
                        for (Header h : response.headers) {
                            if (h.key.toLowerCase().equals("transfer-encoding")) {
                                hasTransferEncoding = true;
                                transferEncoding = h.value;
                                break;
                            }
                        }
                        if (!transferEncoding.equals("chunked")) {
                            throw new IllegalStateException("The response has Transfer-Encoding set to " + transferEncoding + ", cannot run in chunked mode");
                        }
                        if (!hasTransferEncoding) {
                            response.headers.add(new Header("Transfer-Encoding", transferEncoding));
                        }
                        headersSent = true;
                        allowChunked = true;
                        sendResponse(_pctx, response);
                        return this;
                    }

                    @Override
                    public void end(@SuppressWarnings("rawtypes") JSON.Instance inst) {
                        header("Content-Type", "application/json");
                        String ua = headers.get("user-agent");
                        if (ua != null && ua.startsWith("curl/")) {
                            // use pretty
                            end(inst.pretty() + "\r\n");
                        } else {
                            end(inst.stringify());
                        }
                    }

                    @Override
                    public void end(ByteArray body) {
                        if (isEnd) {
                            throw new IllegalStateException("This response is already ended");
                        }
                        isEnd = true;
                        response.body = body;
                        sendResponse(_pctx, response);
                    }

                    @Override
                    public HttpResponse sendChunk(ByteArray chunk) {
                        if (!allowChunked) {
                            throw new IllegalStateException("You have to call sendHeadersWithChunked() first");
                        }
                        Chunk c = new Chunk();
                        c.size = chunk.length();
                        c.content = chunk;
                        _pctx.write(c.toByteArray().toJavaArray());
                        return this;
                    }
                };
            }
            { // chain
                var list = buildHandlerChain(routes.get(method), paths).iterator();
                chain = () -> {
                    var tup = list.next();
                    tup.left.forEach(pre -> pre.accept(ctx[0])); // pre process
                    tup.right.accept(ctx[0]);
                };
            }

            // build ctx
            ctx[0] = new RoutingContext(_pctx.connection.remote, _pctx.connection.getLocal(), method, uri, query, headers, body, response, chain);
        }
        ctx[0].next();
    }

    private static String unescape(String uri) {
        byte[] input = uri.getBytes();
        int idx = 0;
        byte[] result = Utils.allocateByteArray(input.length);
        int state = 0; // 0 -> normal, 1 -> %[x]x, 2 -> %x[x]
        int a = 0;
        for (byte b : input) {
            char c = (char) b; // safe
            if (state == 0) {
                if (c == '%') {
                    state = 1;
                } else {
                    result[idx++] = b;
                }
            } else {
                int n;
                try {
                    n = Integer.parseInt("" + c, 16);
                } catch (NumberFormatException ignore) {
                    assert Logger.lowLevelDebug("escaped uri part not number: " + c);
                    return null;
                }
                if (state == 1) {
                    a = n;
                    state = 2;
                } else {
                    n = a * 16 + n;
                    result[idx++] = (byte) n;
                    state = 0;
                }
            }
        }
        if (state == 0) {
            return new String(result, 0, idx);
        } else {
            return null;
        }
    }

    // return: list<tuple<list<preHandlers>, actualHandler>>
    // the preHandlers are constructed in this method, and actualHandlers are user defined
    private List<Tuple<List<RoutingHandler>, RoutingHandler>> buildHandlerChain(Tree<SubPath, RoutingHandler> tree, List<String> paths) {
        var ret = new LinkedList<Tuple<List<RoutingHandler>, RoutingHandler>>();
        var pre = Collections.<RoutingHandler>emptyList();
        if (paths.isEmpty()) {
            // special handling for paths.isEmpty condition
            // paths.isEmpty means the request path is `/`
            // we need this special handling because
            // the method buildHandlerChain(...) is iterated over paths list
            // if the list is empty, the chain won't be built
            // adding more modifications to method buildHandlerChain(...)
            // will make things complex. we simply check paths.isEmpty here
            // as the entry condition, things will be a lot easier.

            for (var h : tree.leafData()) {
                // definitely no data to fill, so preHandlers can be empty
                ret.add(new Tuple<>(Collections.emptyList(), h));
            }
            // also 404 should be added
            ret.add(new Tuple<>(Collections.emptyList(), this::handle404));
        } else {
            buildHandlerChain(ret, pre, tree, paths, 0);
        }
        assert !ret.isEmpty(); // 404 handler would had already been added
        return ret;
    }

    private void buildHandlerChain(List<Tuple<List<RoutingHandler>, RoutingHandler>> ret,
                                   List<RoutingHandler> preHandlers,
                                   Tree<SubPath, RoutingHandler> tree,
                                   List<String> paths,
                                   final int pathIdx) {
        if (pathIdx >= paths.size()) {
            return; // iteration finishes
        }
        String path = paths.get(pathIdx);
        boolean isLast = pathIdx + 1 == paths.size();
        for (var br : tree.branches()) {
            if (br.data.match(path)) { // may need to handle because sub-path matches
                RoutingHandler preHandler = rctx -> br.data.fill(rctx, path);
                var newPreHandlers = new AppendingList<>(preHandlers, preHandler);

                buildHandlerChain(ret, newPreHandlers, br, paths, pathIdx + 1);

                if (isLast || br.data instanceof WildcardSubPath) {
                    // isLast means in this branch until this tree node, data on these traversed nodes matched
                    // so all leaf/leaves on current tree node should be added to the handler chain

                    for (var h : br.leafData()) {
                        ret.add(new Tuple<>(newPreHandlers, h));
                    }
                }
            }
        }
    }
}
