package vproxy.base.processor.http1;

import vproxy.base.processor.ConnectionDelegate;
import vproxy.base.processor.OOSubContext;
import vproxy.base.processor.Processor;
import vproxy.base.processor.http1.builder.ChunkBuilder;
import vproxy.base.processor.http1.builder.HeaderBuilder;
import vproxy.base.processor.http1.builder.RequestBuilder;
import vproxy.base.processor.http1.builder.ResponseBuilder;
import vproxy.base.processor.http1.entity.Request;
import vproxy.base.processor.http1.entity.Response;
import vproxy.base.util.ByteArray;
import vproxy.base.util.Logger;
import vproxy.base.util.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

@SuppressWarnings("StatementWithEmptyBody")
public class HttpSubContext extends OOSubContext<HttpContext> {
    private int state = 0;
    /*
     * 0 => idle ~> 1 (if request) or -> 22 (if response)
     * 1 => method ~> SP -> 2
     * 2 => uri ~> SP -> 3 or \r\n -> 4
     * 3 => version ~> \r\n -> 4
     * 4 => end-first-line ~> -> 5 or \r\n -> 9
     * 5 => header-key ~> ":" -> 6
     * 6 => header-split ~> -> 7 or \r\n -> 8
     * 7 => header-value ~> \r\n -> 8
     * 8 => end-one-header ~> -> 5 or \r\n -> 9
     * 9 => end-all-headers ~> (if content-length) -> 10 or (if transfer-encoding:chunked) -> 11 or end -> 0
     * 10 => body ~> end -> 0
     * 11 => chunk ~> ";" -> 12 or \r\n -> 14
     * 12 => chunk-extension-split ~> -> 13 or \r\n -> 14
     * 13 => chunk-extension ~> \r\n -> 14
     * 14 => end-chunk-size ~> (if chunk-size) -> 15 or (if !chunk-size) -> 17 or \r\n -> 21
     * 15 => chunk-content ~> \r\n -> 16
     * 16 => end-chunk-content ~> chunk -> 11
     * 17 => trailer-key ~> ":" -> 18
     * 18 => trailer-split ~> -> 19 or \r\n -> 20
     * 19 => trailer-value ~> \r\n -> 20
     * 20 => end-one-trailer ~> -> 17 or \r\n -> 21
     * 21 => end-all ~> 0
     *
     * 22 => response-version ~> SP -> 23
     * 23 => status ~> SP -> 24
     * 24 => reason ~> \r\n -> 4
     */

    private final Handler[] handlers = new Handler[]{
        HttpSubContext.this::state0,
        HttpSubContext.this::state1,
        HttpSubContext.this::state2,
        HttpSubContext.this::state3,
        HttpSubContext.this::state4,
        HttpSubContext.this::state5,
        HttpSubContext.this::state6,
        HttpSubContext.this::state7,
        HttpSubContext.this::state8,
        HttpSubContext.this::state9,
        HttpSubContext.this::state10,
        HttpSubContext.this::state11,
        HttpSubContext.this::state12,
        HttpSubContext.this::state13,
        HttpSubContext.this::state14,
        HttpSubContext.this::state15,
        HttpSubContext.this::state16,
        HttpSubContext.this::state17,
        HttpSubContext.this::state18,
        HttpSubContext.this::state19,
        HttpSubContext.this::state20,
        HttpSubContext.this::state21,
        HttpSubContext.this::state22,
        HttpSubContext.this::state23,
        HttpSubContext.this::state24,
    };

    interface Handler {
        void handle(byte b) throws Exception;
    }

    private byte[] buf;
    private int bufOffset = 0;
    private RequestBuilder req;
    private ResponseBuilder resp;
    private List<HeaderBuilder> headers;
    private HeaderBuilder header;
    private List<ChunkBuilder> chunks;
    private ChunkBuilder chunk;
    private List<HeaderBuilder> trailers;
    private HeaderBuilder trailer;
    private int proxyLen = -1;

    // value of the uri
    // would be used as the hint
    String theUri = null;
    // value of the 'Host: xxx' header
    // would be used as the hint
    // only accessed when it's a frontend sub context
    // if it's a backend sub context, the field might be set but never used
    String theHostHeader = null;
    private StringBuilder theConnectionHeader = null;
    // all these endHeaders fields will set to true in state9 (end-all-headers)
    // if there's no body and chunk, the [0] will be set to false because statm will call end() to reset states
    // otherwise [0] will remain to be true until the body/chunk is properly handled
    // however [1] will always be set to false after state9 finishes in the feed(...) loop
    // and [2] will remain to be true after [0] is set to false in the feed(...) loop
    // to separate these fields is because we need to alert the feed(...) method when the headers end
    // and also we need to know whether we can safely return bytes in the feed(...) method
    //
    // the first line and the headers are deserialized and may be modified before responding
    // so the raw bytes for the headers must not be returned from feed(...).
    //
    // generally:
    // endHeaders[0] means the state machine thinks the headers are ended for current parsing process
    // endHeaders[1] means the headers bytes is going to be serialized
    // endHeaders[2] means the data in method feed(...) can be returned
    private final boolean[] endHeaders = new boolean[]{false, false, false};
    private boolean parserMode;
    // bytes consumed via feed(...) but paused before handling
    ByteArray storedBytesForProcessing = null;

    public HttpSubContext(HttpContext httpContext, int connId, ConnectionDelegate delegate) {
        super(httpContext, connId, delegate);
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
                ret.proxyTODO.connTODO = ctx.connection();
            }
        }
        return ret;
    }

    public int len() {
        if (mode() == Processor.Mode.handle) {
            // do feed, and -1 means feed any data into the processor
            return -1;
        }
        if (ctx.upgradedConnection) {
            return 0x00ffffff; // use max uint24 to prevent some possible overflow
        }
        return proxyLen;
    }

    public void setParserMode() {
        this.parserMode = true;
    }

    public RequestBuilder getParsingReq() {
        return this.req;
    }

    public Request getReq() {
        return this.req.build();
    }

    public Response getResp() {
        return this.resp.build();
    }

    public boolean isIdle() {
        return state == 0;
    }

    public boolean isBeforeBody() {
        return state == 10 || state == 11;
    }

    private Processor.Mode mode() {
        if (ctx.upgradedConnection) {
            return Processor.Mode.proxy;
        }
        switch (state) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 11:
            case 12:
            case 13:
            case 14:
            case 16:
            case 17:
            case 18:
            case 19:
            case 20:
            case 21:
            case 22:
            case 23:
            case 24:
                return Processor.Mode.handle;
            case 10:
            case 15:
                return Processor.Mode.proxy;
        }
        throw new IllegalStateException("BUG: unexpected state " + state);
    }

    public Processor.HandleTODO processorFeed(ByteArray data) throws Exception {
        Processor.HandleTODO handleTODO = Processor.HandleTODO.create();
        handleTODO.send = feedWithStored(data);
        handleTODO.frameEnds = state == 0;
        if (isFrontend()) {
            handleTODO.connTODO = ctx.connection();
        }
        return handleTODO;
    }

    private ByteArray feedWithStored(ByteArray data) throws Exception {
        if (!ctx.frontendExpectingResponse && isFrontend() && data.length() > 0) {
            // the frontend is expecting to receiving response when receiving headers,
            // regardless of whether the request is completely received
            ctx.frontendExpectingResponse = true;
        }
        boolean isIdleBeforeFeeding = state == 0;
        if (storedBytesForProcessing != null) {
            data = storedBytesForProcessing.concat(data);
            storedBytesForProcessing = null;
        }
        ByteArray ret = feed(data);
        boolean isIdleAfterFeeding = state == 0;
        if (isFrontend() && isIdleBeforeFeeding && isIdleAfterFeeding) {
            ctx.currentBackend = -1;
        }
        return ret;
    }

    public ByteArray feed(ByteArray data) throws Exception {
        int consumedBytes = 0;
        ByteArray headersBytes = null;
        while (consumedBytes < data.length()) {
            feed(data.get(consumedBytes++));
            if (parserMode) { // headers manipulation is skipped in parser mode
                continue;
            }

            if (endHeaders[1]) {
                if (isFrontend()) {
                    headersBytes = req.build().toByteArray();
                } else {
                    headersBytes = resp.build().toByteArray();
                }
                // cut the headers part from data
                data = data.sub(consumedBytes, data.length() - consumedBytes);
                consumedBytes = 0;
                // unset the field
                endHeaders[1] = false;
            }

            // handle byte to be proxied but consumed via feed(...)
            if (proxyLen > 0) {
                // need to do proxy
                int originalCursor = consumedBytes;
                consumedBytes += proxyLen;
                if (consumedBytes > data.length()) {
                    // input data has fewer bytes than required
                    // so still need to do proxy later
                    consumedBytes = data.length();
                }
                // need to feed to update the state machine
                for (int i = originalCursor; i < consumedBytes; ++i) {
                    feed(data.get(i));
                }
            }

            // check whether request handling is done
            if (isFrontend() && state == 0 /*idle*/) {
                break;
            }
        }
        if (parserMode) { // ignore pipeline disabling in parser mode
            return data;
        }

        // there might be bytes not processed yet, cut the bytes
        if (consumedBytes < data.length()) {
            storedBytesForProcessing = data.sub(consumedBytes, data.length() - consumedBytes);
            data = data.sub(0, consumedBytes);
        }
        if (!endHeaders[2]) { // return nothing because the headers are not ended yet
            return null;
        }
        if (!endHeaders[0]) {
            endHeaders[2] = false; // processing finished
            if (isFrontend()) {
                delegate.pause(); // pause the requests, will be resumed when response finishes
            }
        }
        // return
        if (headersBytes != null) {
            if (data.length() == 0) {
                return headersBytes;
            } else {
                return headersBytes.concat(data);
            }
        } else {
            return data;
        }
    }

    public void feed(byte b) throws Exception {
        if (state < 0 || state >= handlers.length) {
            throw new IllegalStateException("BUG: unexpected state " + state);
        }
        handlers[state].handle(b);
    }

    private void _proxyDone() {
        proxyLen = -1;
        if (state == 10) {
            end(); // done when body ends
            if (isFrontend()) {
                ctx.currentBackend = -1; // clear backend
            }
        } else if (state == 15) {
            state = 16;
        }
    }

    private Processor.ProxyDoneTODO proxyDone() {
        if (ctx.upgradedConnection) {
            return null;
        }
        int stateWas = this.state;
        _proxyDone();
        if (stateWas == 10) {
            endHeaders[2] = false; // it should be set in feed(...) but there's no chance that feed(...) can be called, so set to false here
        }
        if (state == 0) {
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
        if (!ctx.frontendExpectingResponse) {
            assert Logger.lowLevelDebug("not expecting response, so backend disconnecting is fine");
            return Processor.DisconnectTODO.createSilent();
        }
        if (ctx.frontendExpectingResponseFrom != connId) {
            assert Logger.lowLevelDebug("the disconnected connection is not response connection");
            return Processor.DisconnectTODO.createSilent();
        }
        assert Logger.lowLevelDebug("it's expecting response from the disconnected backend, which is invalid");
        return null;
    }

    // start handler methods

    private void end() {
        state = 0;

        headers = null;
        theConnectionHeader = null;
        endHeaders[0] = false;
        if (!isFrontend() && !parserMode) {
            ctx.clearFrontendExpectingResponse(this);
        }
    }

    private void state0(byte b) {
        if (isFrontend()) {
            req = new RequestBuilder();
            state = 1;
            state1(b);
        } else {
            resp = new ResponseBuilder();
            state = 22;
            state22(b);
        }
    }

    private void state1(byte b) {
        if (b == ' ') {
            state = 2;
        } else {
            req.method.append((char) b);
        }
    }

    private void state2(byte b) {
        if (b == ' ') {
            theUri = req.uri.toString();
            state = 3;
        } else if (b == '\r') {
            // do nothing
        } else if (b == '\n') {
            theUri = req.uri.toString();
            state = 4;
        } else {
            req.uri.append((char) b);
        }
    }

    private void state3(byte b) {
        if (b == '\r') {
            // do nothing
        } else if (b == '\n') {
            state = 4;
        } else {
            if (req.version == null) {
                req.version = new StringBuilder();
            }
            req.version.append((char) b);
        }
    }

    private void state4(byte b) throws Exception {
        if (b == '\r') {
            // do nothing
        } else if (b == '\n') {
            state = 9;
            state9(null);
        } else {
            state = 5;
            state5(b);
        }
    }

    private void state5(byte b) throws Exception {
        if (header == null) {
            header = new HeaderBuilder();
        }

        if (b == ':') {
            state = 6;
            state6(b);
        } else {
            header.key.append((char) b);
        }
    }

    private void state6(byte b) throws Exception {
        if (b == ':') {
            state = 7;
        } else {
            throw new Exception("invalid header: " + header + ", invalid splitter " + (char) b);
        }
    }

    private void state7(byte b) {
        if (b == '\r') {
            // ignore
        } else if (b == '\n') {
            state = 8;
        } else {
            if (b != ' ' || header.value.length() != 0) { // leading spaces of the value are ignored
                header.value.append((char) b);
            }
        }
    }

    private void state8(byte b) throws Exception {
        if (headers == null) {
            headers = new LinkedList<>();
            if (isFrontend()) {
                req.headers = headers;
            } else {
                resp.headers = headers;
            }
        }
        if (header != null) {
            assert Logger.lowLevelDebug("received header " + header);
            boolean addHeader = true;
            if (isFrontend()) { // only modify headers if it's frontend
                String k = header.key.toString().trim().toLowerCase();
                switch (k) {
                    case "host":
                        theHostHeader = header.value.toString().trim();
                        break;
                    case "connection":
                        if (!header.value.toString().trim().equalsIgnoreCase("close")) {
                            theConnectionHeader = header.value;
                            // keep the Connection: xxx header if it's not 'close'
                            break;
                        }
                        // fall through
                    case "x-forwarded-for":
                    case "x-client-port":
                    case "keep-alive":
                        // we remove these headers from request
                        addHeader = false;
                        break;
                }
            }
            if (addHeader) {
                headers.add(header);
            }
            header = null;
        }

        if (b == '\r') {
            // ignore
        } else if (b == '\n') {
            state = 9;
            state9(null);
        } else {
            state = 5;
            state5(b);
        }
    }

    private void addAdditionalHeaders() {
        if (parserMode) { // headers manipulation is skipped in parser mode
            return;
        }
        if (headers == null) {
            headers = new ArrayList<>(2);
            req.headers = headers;
        }
        // x-forwarded-for
        {
            HeaderBuilder h = new HeaderBuilder();
            h.key.append("x-forwarded-for");
            h.value.append(ctx.clientAddress);
            headers.add(h);
            assert Logger.lowLevelDebug("add header " + h);
        }
        // x-client-port
        {
            HeaderBuilder h = new HeaderBuilder();
            h.key.append("x-client-port");
            h.value.append(ctx.clientPort);
            headers.add(h);
            assert Logger.lowLevelDebug("add header " + h);
        }
        // connection
        if (theConnectionHeader == null) {
            HeaderBuilder h = new HeaderBuilder();
            h.key.append("Connection");
            h.value.append("Keep-Alive");
            headers.add(h);
            assert Logger.lowLevelDebug("add header " + h);
        }
    }

    // this method should be called before entering state 9
    // it's for state transferring
    private void state9(@SuppressWarnings("unused") Byte b) {
        Arrays.fill(endHeaders, true);
        if (isFrontend()) {
            addAdditionalHeaders(); // add additional headers
        }
        if (headers == null) {
            end();
            return;
        }
        for (var h : headers) {
            String hdr = h.key.toString().trim();
            if (hdr.equalsIgnoreCase("content-length")) {
                String len = h.value.toString().trim();
                assert Logger.lowLevelDebug("found Content-Length: " + len);
                int intLen = Integer.parseInt(len);
                if (intLen == 0) {
                    end();
                } else {
                    state = 10;
                    proxyLen = intLen;
                }
                return;
            } else if (hdr.equalsIgnoreCase("transfer-encoding")) {
                String encoding = h.value.toString().trim().toLowerCase();
                assert Logger.lowLevelDebug("found Transfer-Encoding: " + encoding);
                if (encoding.equals("chunked")) {
                    state = 11;
                }
                return;
            } else if (hdr.equalsIgnoreCase("upgrade")) {
                if (!parserMode && connId != 0) { // will fallback to raw tcp proxying if the connection is upgraded
                    assert Logger.lowLevelDebug("found upgrade header: " + h.value);
                    ctx.upgradedConnection = true;
                    proxyLen = 0x00ffffff; // use max uint24 to prevent some possible overflow
                }
            }
        }
        assert Logger.lowLevelDebug("Content-Length and Transfer-Encoding both not found");
        end();
    }

    private void state10(byte b) {
        int contentLength = proxyLen;
        --proxyLen;
        if (isFrontend()) {
            if (parserMode) { // use a byte array buffer to hold the data
                if (req.body == null) {
                    buf = Utils.allocateByteArray(contentLength);
                    bufOffset = 0;
                    req.body = ByteArray.from(buf);
                }
                buf[bufOffset++] = b;
            } // else do nothing
        } else {
            if (parserMode) {
                if (resp.body == null) {
                    buf = Utils.allocateByteArray(contentLength);
                    bufOffset = 0;
                    resp.body = ByteArray.from(buf);
                }
                buf[bufOffset++] = b;
            } // else do nothing
        }
        if (proxyLen == 0) {
            buf = null;
            // call proxyDone to generalize the 'handle' mode and 'proxy' mode for the body
            _proxyDone();
        }
    }

    private void state11(byte b) throws Exception {
        if (chunk == null) {
            chunk = new ChunkBuilder();
        }
        if (b == ';') {
            state = 12;
        } else if (b == '\r') {
            // ignore
        } else if (b == '\n') {
            state = 14;
            state14(null);
        } else {
            chunk.size.append((char) b);
        }
    }

    private void state12(byte b) throws Exception {
        if (b == '\r') {
            // ignore
        } else if (b == '\n') {
            state = 14;
            state14(null);
        } else {
            state = 13;
            if (chunk.extension == null) {
                chunk.extension = new StringBuilder();
            }
            chunk.extension.append((char) b);
        }
    }

    private void state13(byte b) throws Exception {
        if (b == '\r') {
            // ignore
        } else if (b == '\n') {
            state = 14;
            state14(null);
        } else {
            chunk.extension.append((char) b);
        }
    }

    // this method may be called before entering state 9
    // it's for state transferring
    private void state14(Byte b) throws Exception {
        int size = chunk == null ? 0 : Integer.parseInt(chunk.size.toString().trim(), 16);
        if (size != 0) {
            state = 15;
            proxyLen = size;
        } else {
            if (b == null) { // called from other states
                // end chunk
                if (chunks == null) {
                    chunks = new LinkedList<>();
                }
                chunks.add(chunk);
                chunk = null;
                if (isFrontend()) {
                    req.chunks = chunks;
                } else {
                    resp.chunks = chunks;
                }
                chunks = null;
            } else {
                if (b == '\r') {
                    // ignore
                } else if (b == '\n') {
                    state = 21;
                    state21(null);
                } else {
                    state = 17;
                    state17(b);
                }
            }
        }
    }

    private void state15(byte b) {
        int contentLength = proxyLen;
        --proxyLen;
        if (parserMode) {
            if (chunk.content == null) {
                buf = Utils.allocateByteArray(contentLength);
                bufOffset = 0;
                chunk.content = ByteArray.from(buf);
            }
            buf[bufOffset++] = b;
        } // else do nothing
        if (proxyLen == 0) {
            buf = null;
            // call proxyDone to generalize the 'handle' mode and 'proxy' mode for the chunk
            _proxyDone();
        }
    }

    private void state16(byte b) throws Exception {
        if (chunks == null) {
            chunks = new LinkedList<>();
        }
        if (chunk != null) {
            chunks.add(chunk);
            chunk = null;
        }

        if (b == '\r') {
            // ignore
        } else if (b == '\n') {
            state = 11;
        } else {
            throw new Exception("invalid chunk end");
        }
    }

    private void state17(byte b) throws Exception {
        if (trailer == null) {
            trailer = new HeaderBuilder();
        }

        if (b == ':') {
            state = 18;
            state18(b);
        } else {
            trailer.key.append((char) b);
        }
    }

    private void state18(byte b) throws Exception {
        if (b == ':') {
            state = 19;
        } else {
            throw new Exception("invalid trailer: " + header + ", invalid splitter " + (char) b);
        }
    }

    private void state19(byte b) {
        if (b == '\r') {
            // ignore
        } else if (b == '\n') {
            state = 20;
        } else {
            if (b != ' ' || trailer.value.length() > 0) { // leading spaces are ignored
                trailer.value.append((char) b);
            }
        }
    }

    private void state20(byte b) throws Exception {
        if (trailers == null) {
            trailers = new LinkedList<>();
        }
        if (trailer != null) {
            assert Logger.lowLevelDebug("received trailer " + trailer);
            trailers.add(trailer);
            trailer = null;
        }

        if (b == '\r') {
            // ignore
        } else if (b == '\n') {
            state = 21;
            if (isFrontend()) {
                req.trailers = trailers;
            } else {
                resp.trailers = trailers;
            }
            trailers = null;
            state21(null);
        } else {
            state = 17;
            state17(b);
        }
    }

    // this method should be called before entering state 9
    // it's for state transferring
    private void state21(@SuppressWarnings("unused") Byte b) {
        end();
    }

    private void state22(byte b) {
        if (b == ' ') {
            state = 23;
        } else {
            resp.version.append((char) b);
        }
    }

    private void state23(byte b) throws Exception {
        if (b == ' ') {
            state = 24;
        } else {
            if (b < '0' || b > '9') {
                throw new Exception("invalid character in http response status code: " + ((char) b));
            }
            resp.statusCode.append((char) b);
        }
    }

    private void state24(byte b) {
        if (b == '\r') {
            // ignore
        } else if (b == '\n') {
            state = 4;
        } else {
            resp.reason.append((char) b);
        }
    }
}
