package vproxybase.processor.http1;

import vproxybase.processor.OOSubContext;
import vproxybase.processor.Processor;
import vproxybase.processor.http1.builder.ChunkBuilder;
import vproxybase.processor.http1.builder.HeaderBuilder;
import vproxybase.processor.http1.builder.RequestBuilder;
import vproxybase.processor.http1.builder.ResponseBuilder;
import vproxybase.processor.http1.entity.Request;
import vproxybase.processor.http1.entity.Response;
import vproxybase.util.ByteArray;
import vproxybase.util.Logger;

import java.util.LinkedList;
import java.util.List;

@SuppressWarnings("StatementWithEmptyBody")
public class HttpSubContext extends OOSubContext<HttpContext> {
    private final boolean frontend;
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
        void handle(ByteArray b) throws Exception;
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
    // value of the Host: header
    // would be used as the hint
    // only accessed when it's a frontend sub context
    // if it's a backend sub context, the field might be set but never used
    // when this field is set to a value, it will not be set to null again
    // because normally a client will not request different hosts in the same connection
    String theHostHeader = null;
    // set this field to true to respond data to the processor lib, otherwise data would be cached
    // only accessed when it's a frontend sub context
    // if it's a backend sub context, the field will be set but never used
    // when this field is set to true, it will not be set to false again
    boolean hostHeaderRetrieved;
    boolean parserMode;

    public HttpSubContext(HttpContext httpContext, int connId) {
        super(httpContext, connId);
        frontend = connId == 0;
        hostHeaderRetrieved = !frontend;
    }

    public void setParserMode() {
        this.parserMode = true;
        this.hostHeaderRetrieved = true; // set this field to true to let feed() respond bytes
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

    @Override
    public Processor.Mode mode() {
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

    @Override
    public boolean expectNewFrame() {
        if (ctx.upgradedConnection) {
            return false;
        }
        return state == 0;
    }

    @Override
    public int len() {
        if (ctx.upgradedConnection) {
            return 0x00ffffff; // use max uint24 to prevent some possible overflow
        }
        // when proxyLen == -1, do feed, and -1 means feed any data into the processor
        return proxyLen;
    }

    private boolean passParam_TryFillAdditionalHeaders = false;
    private ByteArray storedBytes = null;

    @Override
    public ByteArray feed(ByteArray data) throws Exception {
        boolean isIdleBeforeFeeding = state == 0;
        ByteArray ret = feed1(data);
        boolean isIdleAfterFeeding = state == 0;
        if (isFrontend() && isIdleBeforeFeeding && isIdleAfterFeeding) {
            ctx.currentBackend = -1;
        }
        return ret;
    }

    private ByteArray feed1(ByteArray data) throws Exception {
        int consumedBytes = 0;
        while (consumedBytes < data.length()) {
            feed(data.get(consumedBytes++));

            if (passParam_TryFillAdditionalHeaders) {
                boolean noForwardedFor = true;
                boolean noClientPort = true;

                for (HeaderBuilder h : headers) {
                    String hv = h.key.toString().trim();
                    if (hv.equalsIgnoreCase("x-forwarded-for")) {
                        noForwardedFor = false;
                    } else if (hv.equalsIgnoreCase("x-client-port")) {
                        noClientPort = false;
                    }
                    if (!noForwardedFor && !noClientPort) {
                        break;
                    }
                }
                ByteArray appendData = null;
                if (noForwardedFor) {
                    ByteArray b = ByteArray.from(("" +
                        "x-forwarded-for: " + ctx.clientAddress + "\r\n" +
                        "").getBytes());
                    assert Logger.lowLevelDebug("add header x-forwarded-for: " + ctx.clientAddress);
                    appendData = b;
                }
                if (noClientPort) {
                    ByteArray b = ByteArray.from(("" +
                        "x-client-port: " + ctx.clientPort + "\r\n" +
                        "").getBytes());
                    assert Logger.lowLevelDebug("add header x-client-port: " + ctx.clientPort);
                    if (appendData == null) {
                        appendData = b;
                    } else {
                        appendData = appendData.concat(b);
                    }
                }
                if (appendData != null) {
                    // insert the appendData into data
                    data = data.sub(0, consumedBytes - 1)
                        .concat(appendData)
                        .concat(data.sub(consumedBytes - 1, data.length() - (consumedBytes - 1)));
                    consumedBytes += appendData.length();
                }
                passParam_TryFillAdditionalHeaders = false;
            }
            if (proxyLen > 0) {
                // need to do proxy
                int originalCursor = consumedBytes;
                consumedBytes += proxyLen;
                if (consumedBytes > data.length()) {
                    // input data has fewer bytes than required
                    // so still need to do proxy later
                    consumedBytes = data.length();
                }
                for (int i = originalCursor; i < consumedBytes; ++i) {
                    feed(data.get(i));
                }
            }
        }
        if (hostHeaderRetrieved) {
            if (storedBytes == null) {
                return data;
            } else {
                ByteArray arr = storedBytes;
                storedBytes = null;
                return arr.concat(data);
            }
        } else {
            if (storedBytes == null) {
                storedBytes = data;
            } else {
                storedBytes = storedBytes.concat(data);
            }
            return null;
        }
    }

    public void feed(byte b) throws Exception {
        if (state < 0 || state >= handlers.length) {
            throw new IllegalStateException("BUG: unexpected state " + state);
        }
        handlers[state].handle(ByteArray.from(b));
    }

    @Override
    public ByteArray produce() {
        return null; // never produce data
    }

    @Override
    public void proxyDone() {
        if (ctx.upgradedConnection) {
            return;
        }
        proxyLen = -1;
        if (state == 10) {
            end(); // done when body ends
        } else if (state == 15) {
            state = 16;
        }
    }

    @Override
    public ByteArray connected() {
        return null; // never respond when connected
    }

    // start handler methods

    private void end() {
        state = 0;
    }

    private void state0(ByteArray data) {
        if (frontend) {
            req = new RequestBuilder();
            state = 1;
            state1(data);
        } else {
            resp = new ResponseBuilder();
            state = 22;
            state22(data);
        }
    }

    private void state1(ByteArray data) {
        int b = data.uint8(0);
        if (b == ' ') {
            state = 2;
        } else {
            req.method.append((char) b);
        }
    }

    private void state2(ByteArray data) {
        int b = data.uint8(0);
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

    private void state3(ByteArray data) {
        int b = data.uint8(0);
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

    private void state4(ByteArray data) throws Exception {
        int b = data.uint8(0);
        if (b == '\r') {
            // do nothing
        } else if (b == '\n') {
            state = 9;
            state9(null);
        } else {
            state = 5;
            state5(data);
        }
    }

    private void state5(ByteArray data) throws Exception {
        if (header == null) {
            header = new HeaderBuilder();
        }

        int b = data.uint8(0);
        if (b == ':') {
            state = 6;
            state6(data);
        } else {
            header.key.append((char) b);
        }
    }

    private void state6(ByteArray data) throws Exception {
        int b = data.uint8(0);
        if (b == ':') {
            state = 7;
        } else {
            throw new Exception("invalid header: " + header + ", invalid splitter " + (char) b);
        }
    }

    private void state7(ByteArray data) {
        int b = data.uint8(0);
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

    private void state8(ByteArray data) throws Exception {
        if (headers == null) {
            headers = new LinkedList<>();
            if (frontend) {
                req.headers = headers;
            } else {
                resp.headers = headers;
            }
        }
        if (header != null) {
            headers.add(header);
            assert Logger.lowLevelDebug("received header " + header);
            String k = header.key.toString();
            if (k.trim().equalsIgnoreCase("host")) {
                theHostHeader = header.value.toString().trim();
                hostHeaderRetrieved = true;
            }
            header = null;
        }

        int b = data.uint8(0);
        if (b == '\r') {
            // ignore
            passParam_TryFillAdditionalHeaders = frontend; // only add header if it's from frontend
        } else if (b == '\n') {
            state = 9;
            state9(null);
        } else {
            state = 5;
            state5(data);
        }
    }

    // this method should be called before entering state 9
    // it's for state transferring
    private void state9(@SuppressWarnings("unused") ByteArray data) {
        // ignore the data
        hostHeaderRetrieved = true;
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
                    return;
                }
            }
        }
        assert Logger.lowLevelDebug("Content-Length and Transfer-Encoding both not found");
        end();
    }

    private void state10(ByteArray data) {
        assert data.length() <= proxyLen;
        int contentLength = proxyLen;
        proxyLen -= data.length();
        if (frontend) {
            if (parserMode) { // use a byte array buffer to hold the data
                if (req.body == null) {
                    buf = new byte[contentLength];
                    bufOffset = 0;
                    req.body = ByteArray.from(buf);
                }
                for (int i = 0; i < data.length(); ++i) {
                    buf[bufOffset++] = data.get(i);
                }
            } else {
                if (req.body == null) {
                    req.body = data;
                } else {
                    req.body = req.body.concat(data);
                }
            }
        } else {
            if (parserMode) {
                if (resp.body == null) {
                    buf = new byte[contentLength];
                    bufOffset = 0;
                    resp.body = ByteArray.from(buf);
                }
                for (int i = 0; i < data.length(); ++i) {
                    buf[bufOffset++] = data.get(i);
                }
            } else {
                if (resp.body == null) {
                    resp.body = data;
                } else {
                    resp.body = resp.body.concat(data);
                }
            }
        }
        if (proxyLen == 0) {
            buf = null;
            // the method will not be called if it's using the Proxy lib
            // so proxyDone will not be called either
            // we call it manually here
            proxyDone();
        }
    }

    private void state11(ByteArray data) throws Exception {
        if (chunk == null) {
            chunk = new ChunkBuilder();
        }
        int b = data.uint8(0);
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

    private void state12(ByteArray data) throws Exception {
        int b = data.uint8(0);
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

    private void state13(ByteArray data) throws Exception {
        int b = data.uint8(0);
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
    private void state14(ByteArray data) throws Exception {
        int size = chunk == null ? 0 : Integer.parseInt(chunk.size.toString().trim(), 16);
        if (size != 0) {
            state = 15;
            proxyLen = size;
        } else {
            if (data == null) { // called from other states
                // end chunk
                if (chunks == null) {
                    chunks = new LinkedList<>();
                }
                chunks.add(chunk);
                chunk = null;
                if (frontend) {
                    req.chunks = chunks;
                } else {
                    resp.chunks = chunks;
                }
                chunks = null;
            } else {
                int b = data.uint8(0);
                if (b == '\r') {
                    // ignore
                } else if (b == '\n') {
                    state = 21;
                    state21(null);
                } else {
                    state = 17;
                    state17(data);
                }
            }
        }
    }

    private void state15(ByteArray data) {
        assert data.length() <= proxyLen;
        int contentLength = proxyLen;
        proxyLen -= data.length();
        if (parserMode) {
            if (chunk.content == null) {
                buf = new byte[contentLength];
                bufOffset = 0;
                chunk.content = ByteArray.from(buf);
            }
            for (int i = 0; i < data.length(); ++i) {
                buf[bufOffset++] = data.get(i);
            }
        } else {
            if (chunk.content == null) {
                chunk.content = data;
            } else {
                chunk.content = chunk.content.concat(data);
            }
        }
        if (proxyLen == 0) {
            buf = null;
            // this method will not be called if using the Proxy lib
            // and proxyDone will not be called as well
            // so call it manually here
            proxyDone();
        }
    }

    private void state16(ByteArray data) throws Exception {
        if (chunks == null) {
            chunks = new LinkedList<>();
        }
        if (chunk != null) {
            chunks.add(chunk);
            chunk = null;
        }

        int b = data.uint8(0);
        if (b == '\r') {
            // ignore
        } else if (b == '\n') {
            state = 11;
        } else {
            throw new Exception("invalid chunk end");
        }
    }

    private void state17(ByteArray data) throws Exception {
        if (trailer == null) {
            trailer = new HeaderBuilder();
        }

        int b = data.uint8(0);
        if (b == ':') {
            state = 18;
            state18(data);
        } else {
            trailer.key.append((char) b);
        }
    }

    private void state18(ByteArray data) throws Exception {
        int b = data.uint8(0);
        if (b == ':') {
            state = 19;
        } else {
            throw new Exception("invalid trailer: " + header + ", invalid splitter " + (char) b);
        }
    }

    private void state19(ByteArray data) {
        int b = data.uint8(0);
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

    private void state20(ByteArray data) throws Exception {
        if (trailers == null) {
            trailers = new LinkedList<>();
        }
        if (trailer != null) {
            assert Logger.lowLevelDebug("received trailer " + trailer);
            trailers.add(trailer);
            trailer = null;
        }

        int b = data.uint8(0);
        if (b == '\r') {
            // ignore
        } else if (b == '\n') {
            state = 21;
            if (frontend) {
                req.trailers = trailers;
            } else {
                resp.trailers = trailers;
            }
            trailers = null;
            state21(null);
        } else {
            state = 17;
            state17(data);
        }
    }

    // this method should be called before entering state 9
    // it's for state transferring
    private void state21(@SuppressWarnings("unused") ByteArray data) {
        end();
    }

    private void state22(ByteArray data) {
        int b = data.uint8(0);
        if (b == ' ') {
            state = 23;
        } else {
            resp.version.append((char) b);
        }
    }

    private void state23(ByteArray data) throws Exception {
        int b = data.uint8(0);
        if (b == ' ') {
            state = 24;
        } else {
            if (b < '0' || b > '9') {
                throw new Exception("invalid character in http response status code: " + ((char) b));
            }
            resp.statusCode.append((char) b);
        }
    }

    private void state24(ByteArray data) {
        int b = data.uint8(0);
        if (b == '\r') {
            // ignore
        } else if (b == '\n') {
            state = 4;
        } else {
            resp.reason.append((char) b);
        }
    }
}
