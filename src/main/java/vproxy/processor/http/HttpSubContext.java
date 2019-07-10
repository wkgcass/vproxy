package vproxy.processor.http;

import vproxy.processor.OOSubContext;
import vproxy.processor.Processor;
import vproxy.processor.http.builder.ChunkBuilder;
import vproxy.processor.http.builder.HeaderBuilder;
import vproxy.processor.http.builder.RequestBuilder;
import vproxy.processor.http.builder.ResponseBuilder;
import vproxy.processor.http.entity.Request;
import vproxy.processor.http.entity.Response;
import vproxy.util.ByteArray;
import vproxy.util.Logger;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
     * 11 => chunk ~> ":" -> 12 or \r\n -> 14
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

    private Map<Integer, Handler> handers = new HashMap<>() {{
        put(0, HttpSubContext.this::state0);
        put(1, HttpSubContext.this::state1);
        put(2, HttpSubContext.this::state2);
        put(3, HttpSubContext.this::state3);
        put(4, HttpSubContext.this::state4);
        put(5, HttpSubContext.this::state5);
        put(6, HttpSubContext.this::state6);
        put(7, HttpSubContext.this::state7);
        put(8, HttpSubContext.this::state8);
        put(9, HttpSubContext.this::state9);
        put(10, HttpSubContext.this::state10);
        put(11, HttpSubContext.this::state11);
        put(12, HttpSubContext.this::state12);
        put(13, HttpSubContext.this::state13);
        put(14, HttpSubContext.this::state14);
        put(15, HttpSubContext.this::state15);
        put(16, HttpSubContext.this::state16);
        put(17, HttpSubContext.this::state17);
        put(18, HttpSubContext.this::state18);
        put(19, HttpSubContext.this::state19);
        put(20, HttpSubContext.this::state20);
        put(21, HttpSubContext.this::state21);
        put(22, HttpSubContext.this::state22);
        put(23, HttpSubContext.this::state23);
        put(24, HttpSubContext.this::state24);
    }};

    interface Handler {
        void handle(ByteArray data) throws Exception;
    }

    private RequestBuilder req;
    private ResponseBuilder resp;
    private List<HeaderBuilder> headers;
    private HeaderBuilder header;
    private List<ChunkBuilder> chunks;
    private ChunkBuilder chunk;
    private List<HeaderBuilder> trailers;
    private HeaderBuilder trailer;
    private int proxyLen = -1;

    public HttpSubContext(HttpContext httpContext, int connId) {
        super(httpContext, connId);
        frontend = connId == 0;
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

    @Override
    public Processor.Mode mode() {
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
        return state == 0;
    }

    @Override
    public int len() {
        return proxyLen == -1 ? 1 : proxyLen;
    }

    @Override
    public ByteArray feed(ByteArray data) throws Exception {
        Handler handler = handers.get(state);
        if (handler == null)
            throw new IllegalStateException("BUG: unexpected state " + state);
        handler.handle(data);
        return data;
    }

    @Override
    public ByteArray produce() {
        return null; // never produce data
    }

    @Override
    public void proxyDone() {
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
            state = 3;
        } else if (b == '\r') {
            // do nothing
        } else if (b == '\n') {
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
            header = null;
        }

        int b = data.uint8(0);
        if (b == '\r') {
            // ignore
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
        if (headers == null) {
            end();
            return;
        }
        for (var h : headers) {
            String hdr = h.key.toString().trim().toLowerCase();
            if (hdr.equals("content-length")) {
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
            } else if (hdr.equals("transfer-encoding")) {
                String encoding = h.value.toString().trim().toLowerCase();
                assert Logger.lowLevelDebug("found Transfer-Encoding: " + encoding);
                if (encoding.equals("chunked")) {
                    state = 11;
                }
                return;
            }
        }
        assert Logger.lowLevelDebug("Content-Length and Transfer-Encoding both not found");
        end();
    }

    private void state10(ByteArray data) {
        assert data.length() <= proxyLen;
        proxyLen -= data.length();
        if (frontend) {
            if (req.body == null) {
                req.body = data;
            } else {
                req.body = req.body.concat(data);
            }
        } else {
            if (resp.body == null) {
                resp.body = data;
            } else {
                resp.body = resp.body.concat(data);
            }
        }
        if (proxyLen == 0) {
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
        if (b == ':') {
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
        proxyLen -= data.length();
        if (chunk.content == null) {
            chunk.content = data;
        } else {
            chunk.content = chunk.content.concat(data);
        }
        if (proxyLen == 0) {
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

    private void state23(ByteArray data) {
        int b = data.uint8(0);
        if (b == ' ') {
            state = 24;
        } else {
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
