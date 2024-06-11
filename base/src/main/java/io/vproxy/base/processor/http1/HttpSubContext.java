package io.vproxy.base.processor.http1;

import io.vproxy.base.http.HttpParserHelper;
import io.vproxy.base.http.HttpReqParser;
import io.vproxy.base.http.HttpRespParser;
import io.vproxy.base.http.IHttpParser;
import io.vproxy.base.processor.ConnectionDelegate;
import io.vproxy.base.processor.OOSubContext;
import io.vproxy.base.processor.Processor;
import io.vproxy.base.processor.http1.builder.HeaderBuilder;
import io.vproxy.base.processor.http1.entity.Request;
import io.vproxy.base.processor.http1.entity.Response;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.nio.ByteArrayChannel;

import java.util.ArrayList;

public class HttpSubContext extends OOSubContext<HttpContext> {
    private static final ByteArray SIMPLE_LAST_CHUNK = ByteArray.from("0\r\n");

    final HttpReqParser reqParser;
    private final HttpRespParser respParser;

    // bytes consumed via feed(...) but paused before handling
    ByteArray storedBytesForProcessing = null;
    // connection will be closed or should be closed after sending all data
    boolean closeConnection = false; // TODO consider this field in the future

    public HttpSubContext(HttpContext httpContext, int connId, ConnectionDelegate delegate) {
        super(httpContext, connId, delegate);
        if (isFrontend()) {
            reqParser = new HttpReqParser(new HttpReqParser.Params()
                .setBuildResult(false)
                .setSegmentedParsing(true));
            respParser = null;
            httpContext.frontendContext = this;
        } else {
            reqParser = null;
            respParser = new HttpRespParser(new HttpRespParser.Params()
                .setBuildResult(false)
                .setSegmentedParsing(true));
        }
    }

    private IHttpParser getParser() {
        if (isFrontend()) {
            return reqParser;
        } else {
            return respParser;
        }
    }

    @Override
    public Processor.ProcessorTODO process() {
        Processor.ProcessorTODO ret;
        if (mode() == Processor.Mode.handle) {
            ret = Processor.ProcessorTODO.create();
            ret.mode = Processor.Mode.handle;
            ret.len = len();
            ret.feed = this::processorFeed;
        } else {
            ret = Processor.ProcessorTODO.createProxy();
            ret.len = len();
            ret.proxyTODO.proxyDone = this::proxyDone;
            if (isFrontend()) {
                assert ctx.currentBackend != -1;
                ret.proxyTODO.connTODO = ctx.connection(ctx.currentBackend);
            }
        }
        return ret;
    }

    private int len() {
        // if there's data stored, we first handle the stored data
        if (storedBytesForProcessing != null) {
            return 0; // 0 will skip the input data existence check
        }
        // normal handling ...
        if (mode() == Processor.Mode.handle) {
            // do feed, and -1 means feed any data into the processor
            return -1;
        }
        // if the connection is upgraded, we simply proxy everything
        if (ctx.upgradedConnection) {
            return 0x00ffffff; // use max uint24 to prevent some possible overflow
        }
        // normal proxying ...
        return getParser().getBuilder().dataLength;
    }

    @Override
    public boolean isIdle() {
        return getParser().getState() == 0;
    }

    private Processor.Mode mode() {
        // if there's data stored, we must handle it first
        if (storedBytesForProcessing != null) {
            return Processor.Mode.handle;
        }
        // if the connection is upgraded, we simply proxy everything
        if (ctx.upgradedConnection) {
            return Processor.Mode.proxy;
        }
        // normal handling ...
        int state = getParser().getState();
        return switch (state) {
            case HttpParserHelper.STATE_BODY,
                 HttpParserHelper.STATE_CHUNK_CONTENT -> Processor.Mode.proxy;
            default -> Processor.Mode.handle;
        };
    }

    private Processor.HandleTODO processorFeed(ByteArray data) throws Exception {
        // if the connection is upgraded, then simply proxy the data
        if (ctx.upgradedConnection) {
            var handleTODO = Processor.HandleTODO.create();
            if (storedBytesForProcessing != null) {
                handleTODO.send = storedBytesForProcessing.concat(data);
                storedBytesForProcessing = null;
            } else {
                handleTODO.send = data;
            }
            if (isFrontend()) {
                handleTODO.connTODO = ctx.connection(ctx.currentBackend);
            }
            return handleTODO;
        }

        // normal handling ...

        Processor.HandleTODO handleTODO = Processor.HandleTODO.create();
        handleTODO.send = feedWithStored(data);
        handleTODO.frameEnds = isIdle();
        if (isFrontend()) {
            handleTODO.connTODO = ctx.connection(ctx.currentBackend);
            if (isIdle()) {
                onFrontendRequestDone();
            }
        } else { // is backend
            // response finished or connection upgraded
            if (isIdle() || ctx.upgradedConnection) {
                handleTODO.sendDone = this::onBackendResponseDone;
            }
        }
        return handleTODO;
    }

    private void onFrontendRequestDone() {
        delegate.pause(); // need to wait for response
    }

    private void onBackendResponseDone(Processor.HandleTODO unused) {
        ctx.currentBackend = -1;
        ctx.frontendContext.delegate.resume();
    }

    private ByteArray feedWithStored(ByteArray data) throws Exception {
        if (storedBytesForProcessing != null) {
            data = storedBytesForProcessing.concat(data);
            storedBytesForProcessing = null;
        }
        return feed(data.toFullChannel());
    }

    private ByteArray feed(ByteArrayChannel chnl) throws Exception {
        var ret = ByteArray.allocate(0);
        while (chnl.used() != 0) {
            var parser = getParser();

            int res = parser.feed(chnl);
            if (res == -1) {
                var err = parser.getErrorMessage();
                if (err != null) {
                    throw new Exception("invalid " + (isFrontend() ? "request" : "response") + ": " + err);
                }
                // want more data
                break;
            }

            var state = parser.getState();
            if (state == HttpParserHelper.STATE_END_ALL_HEADERS) {
                if (isFrontend()) {
                    ret = ret.concat(
                        handleFrontendHeaders());
                } else {
                    ret = ret.concat(
                        handleBackendHeaders());
                    var resp = respParser.getBuilder();
                    if (resp.statusCode.toString().trim().equals("101")) {
                        // switch protocol
                        ctx.upgradedConnection = true;
                        // use uint24 to prevent possible overflow
                        resp.dataLength = Math.max(chnl.used(), 0x00ffffff);
                        if (chnl.used() > 0) {
                            ret = doProxyInsideFeed(chnl, ret);
                        }
                        return ret;
                    }
                }
                state = parser.nextState();
                // fallthrough
            }
            if (state == HttpParserHelper.STATE_BODY) {
                var builder = parser.getBuilder();
                ret = doProxyInsideFeed(chnl, ret);
                if (builder.dataLength == 0) {
                    parser.nextState();
                    assert parser.getState() == HttpParserHelper.STATE_IDLE;
                }
                break;
            }
            if (state == HttpParserHelper.STATE_CHUNK_BEGIN) {
                continue;
            }
            if (state == HttpParserHelper.STATE_CHUNK_CONTENT) {
                var builder = parser.getBuilder();
                var chunk = builder.chunk;
                ret = ret.concat(
                    chunk.headToByteArray());
                ret = doProxyInsideFeed(chnl, ret);
                continue;
            }
            if (state == HttpParserHelper.STATE_END_CHUNK) {
                ret = ret.concat("\r\n");
                continue;
            }
            if (state == HttpParserHelper.STATE_END_ALL_CHUNKS) {
                var builder = parser.getBuilder();
                var chunks = builder.chunks;
                if (chunks == null || chunks.isEmpty() || !chunks.getLast().size.toString().trim().equals("0")) {
                    throw new Exception("invalid " + (isFrontend() ? "request" : "response") + ": no last-chunk");
                }
                var last = chunks.getLast();
                if (last.extension == null) {
                    ret = ret.concat(SIMPLE_LAST_CHUNK);
                } else {
                    // need to serialize last-chunk
                    ret = ret.concat(chunks.getLast().build().toByteArray());
                }
                parser.nextState();
                continue;
            }
            if (state == HttpParserHelper.STATE_END_ALL_TRAILERS) {
                var builder = parser.getBuilder();
                ret = ret.concat(builder.trailersToByteArray());
                ret = ret.concat("\r\n"); // trailers ends with \r\n
                parser.nextState();
                assert parser.getState() == HttpParserHelper.STATE_IDLE;
                break;
            }
            if (state == HttpParserHelper.STATE_IDLE) {
                break;
            }
            throw new Exception("invalid " + (isFrontend() ? "request" : "response") + ": unexpected state: " + state);
        }
        if (chnl.used() > 0) {
            storedBytesForProcessing = chnl.getArray().sub(chnl.getReadOff(), chnl.used());
        }
        if (ret.length() == 0) {
            return null;
        } else {
            return ret;
        }
    }

    private ByteArray doProxyInsideFeed(ByteArrayChannel chnl, ByteArray ret) {
        var builder = getParser().getBuilder();
        var chunkLength = builder.dataLength;
        if (chnl.used() > 0) {
            int fromChnlLen = Math.min(chnl.used(), chunkLength);
            ret = ret.concat(chnl.getArray().sub(chnl.getReadOff(), fromChnlLen));
            chnl.skip(fromChnlLen);
            builder.dataLength -= fromChnlLen;
        }
        return ret;
    }

    private ByteArray handleBackendHeaders() throws Exception {
        var builder = respParser.getBuilder();
        var resp = builder.build();
        var version = resp.version.trim();
        if (!version.equals("HTTP/1.1") && !version.equals("HTTP/1.0")) {
            throw new Exception("only supports HTTP/1.1 or HTTP/1.0, but got " + version);
        }
        return resp.toByteArray();
    }

    private static final char[] HTTP_HEADER_X_FORWARDED_FOR = "x-forwarded-for".toCharArray();
    private static final char[] HTTP_HEADER_X_CLIENT_PORT = "x-client-port".toCharArray();
    private static final char[] HTTP_HEADER_CONNECTION = "connection".toCharArray();
    private static final char[] HTTP_HEADER_KEEP_ALIVE = "keep-alive".toCharArray();

    private ByteArray handleFrontendHeaders() throws Exception {
        var builder = reqParser.getBuilder();
        if (builder.version == null) {
            throw new Exception("HTTP/0.9 is not supported");
        }
        var version = builder.version.toString();
        if (!version.equals("HTTP/1.1") && !version.equals("HTTP/1.0")) {
            throw new Exception("only supports HTTP/1.1 or HTTP/1.0, but got " + version);
        }
        var isKeepalive = version.equals("HTTP/1.1");
        if (builder.headers == null) {
            builder.headers = new ArrayList<>();
        }
        var headers = builder.headers;

        boolean[] replaced = new boolean[]{
            false, /* xff */
            false, /* x-client-port */
        };
        for (var iterator = headers.iterator(); iterator.hasNext(); ) {
            var h = iterator.next();
            if (h.keyEqualsIgnoreCase(HTTP_HEADER_X_FORWARDED_FOR)) {
                h.value.delete(0, h.value.length());
                h.value.append(ctx.clientAddress);
                replaced[0] = true;
            } else if (h.keyEqualsIgnoreCase(HTTP_HEADER_X_CLIENT_PORT)) {
                h.value.delete(0, h.value.length());
                h.value.append(ctx.clientPort);
                replaced[1] = true;
            } else if (h.keyEqualsIgnoreCase(HTTP_HEADER_CONNECTION)) {
                var v = h.value.toString();
                if (v.equalsIgnoreCase("keep-alive")) {
                    isKeepalive = true;
                } else if (v.equalsIgnoreCase("close")) {
                    assert Logger.lowLevelDebug("Connection header is removed, original value is " + h.valueAsString());
                    isKeepalive = false;
                    iterator.remove();
                } else if (v.equalsIgnoreCase("upgrade")) {
                    isKeepalive = true; // upgraded connection will be treated as a simple tcp connection
                }
            } else if (h.keyEqualsIgnoreCase(HTTP_HEADER_KEEP_ALIVE)) {
                assert Logger.lowLevelDebug("Keep-Alive header is removed, original value is " + h.valueAsString());
                iterator.remove();
            }
        }
        if (!replaced[0]) {
            var hb = new HeaderBuilder();
            hb.key.append("X-Forwarded-For");
            hb.value.append(ctx.clientAddress);
            headers.add(hb);
        }
        if (!replaced[1]) {
            var hb = new HeaderBuilder();
            hb.key.append("X-Client-Port");
            hb.value.append(ctx.clientPort);
            headers.add(hb);
        }

        closeConnection = !isKeepalive;
        return builder.build().toByteArray();
    }

    private Processor.ProxyDoneTODO proxyDone() {
        if (ctx.upgradedConnection) {
            return null;
        }
        getParser().nextState();
        if (isIdle()) { // request or proxy done
            if (isFrontend()) {
                onFrontendRequestDone();
            } else {
                onBackendResponseDone(null);
            }
            return Processor.ProxyDoneTODO.createFrameEnds();
        } else {
            return null;
        }
    }

    @Override
    public Processor.HandleTODO connected() {
        return null; // never respond when connected
    }

    @Override
    public Processor.HandleTODO remoteClosed() {
        return null; // TODO handle http/1.0 without content-length and not chunked
    }

    @Override
    public Processor.DisconnectTODO disconnected(boolean exception) {
        if (ctx.currentBackend == -1) {
            assert Logger.lowLevelDebug("not expecting response, so backend disconnecting is fine");
            return Processor.DisconnectTODO.createSilent();
        }
        if (ctx.currentBackend != connId) {
            assert Logger.lowLevelDebug("the disconnected connection is not response connection");
            return Processor.DisconnectTODO.createSilent();
        }
        assert Logger.lowLevelDebug("it's expecting response from the disconnected backend, which is invalid");
        return null;
    }

    // ---------------- for unit tests ----------------

    public ByteArray unittest_feed(ByteArray data) throws Exception {
        return feed(data.toFullChannel());
    }

    public int unittest_len() {
        return len();
    }

    public Processor.Mode unittest_mode() {
        return mode();
    }

    public Request unittest_getReq() {
        return reqParser.getBuilder().build();
    }

    public Response unittest_getResp() {
        return respParser.getBuilder().build();
    }
}
