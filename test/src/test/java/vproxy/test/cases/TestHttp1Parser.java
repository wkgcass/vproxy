package vproxy.test.cases;

import org.junit.Test;
import vfd.IP;
import vfd.IPPort;
import vproxybase.processor.Processor;
import vproxybase.processor.http1.HttpContext;
import vproxybase.processor.http1.HttpProcessor;
import vproxybase.processor.http1.HttpSubContext;
import vproxybase.processor.http1.entity.Request;
import vproxybase.processor.http1.entity.Response;
import vproxybase.util.ByteArray;

import java.util.LinkedHashMap;
import java.util.Objects;

import static org.junit.Assert.*;

public class TestHttp1Parser {
    private static final String forwardedFor = "1.2.3.4";
    private static final String clientPort = "1122";
    private static final IPPort address = new IPPort(
        IP.from(Objects.requireNonNull(IP.parseIpv4String(forwardedFor))),
        Integer.parseInt(clientPort)
    );

    @Test
    public void simpleRequest() throws Exception {
        Processor<HttpContext, HttpSubContext> p = new HttpProcessor();
        HttpContext ctx = p.init(address);
        HttpSubContext front = p.initSub(ctx, 0, null);
        front.setParserMode();

        String reqHead = "" +
            "GET /hello/url HTTP/1.1\r\n" +
            "Host: www.example.com\r\n" +
            "Hello: World\r\n" +
            "\r\n" +
            "";
        byte[] reqHeadBytes = reqHead.getBytes();

        for (byte b : reqHeadBytes) {
            int len = front.len();
            assertEquals(-1, len);
            ByteArray a = ByteArray.from(b);
            ByteArray r = front.feed(a);
            if (r.length() == 1) {
                assertEquals(a, r);
            } else {
                assertEquals(ByteArray.from(("" +
                    "x-forwarded-for: " + forwardedFor + "\r\n" +
                    "x-client-port: " + clientPort + "\r\n" +
                    "").getBytes()).concat(a), r);
            }
        }
        Request req = front.getReq();
        {
            assertEquals("GET", req.method);
            assertEquals("/hello/url", req.uri);
            assertEquals("HTTP/1.1", req.version);

            assertEquals("Host", req.headers.get(0).key);
            assertEquals("www.example.com", req.headers.get(0).value);

            assertEquals("Hello", req.headers.get(1).key);
            assertEquals("World", req.headers.get(1).value);
        }
        assertTrue(front.isIdle());
    }

    @Test
    public void simpleResponse() throws Exception {
        Processor<HttpContext, HttpSubContext> p = new HttpProcessor();
        HttpContext ctx = p.init(null);
        HttpSubContext backend = p.initSub(ctx, 1, null);
        backend.setParserMode();

        String respHead = "" +
            "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/json\r\n" +
            "\r\n";
        byte[] respHeadBytes = respHead.getBytes();

        for (byte b : respHeadBytes) {
            int len = backend.len();
            assertEquals(-1, len);
            ByteArray a = ByteArray.from(b);
            assertEquals(a, backend.feed(a));
        }
        Response resp = backend.getResp();
        {
            assertEquals("HTTP/1.1", resp.version);
            assertEquals(200, resp.statusCode);
            assertEquals("OK", resp.reason);

            assertEquals("Content-Type", resp.headers.get(0).key);
            assertEquals("application/json", resp.headers.get(0).value);
        }
        assertTrue(backend.isIdle());
    }

    @Test
    public void noHeaderRequest() throws Exception {
        Processor<HttpContext, HttpSubContext> p = new HttpProcessor();
        HttpContext ctx = p.init(null);
        HttpSubContext front = p.initSub(ctx, 0, null);
        front.setParserMode();

        String reqHead = "" +
            "GET /hello/url HTTP/1.1\r\n" +
            "\r\n" +
            "";
        byte[] reqHeadBytes = reqHead.getBytes();

        for (byte b : reqHeadBytes) {
            int len = front.len();
            assertEquals(-1, len);
            ByteArray a = ByteArray.from(b);
            assertEquals(a, front.feed(a));
        }
        Request req = front.getReq();
        {
            assertEquals("GET", req.method);
            assertEquals("/hello/url", req.uri);
            assertEquals("HTTP/1.1", req.version);

            assertNull(req.headers);
        }
        assertTrue(front.isIdle());
    }

    @Test
    public void noHeaderResponse() throws Exception {
        Processor<HttpContext, HttpSubContext> p = new HttpProcessor();
        HttpContext ctx = p.init(null);
        HttpSubContext backend = p.initSub(ctx, 1, null);
        backend.setParserMode();

        String respHead = "" +
            "HTTP/1.1 200 OK\r\n" +
            "\r\n";
        byte[] respHeadBytes = respHead.getBytes();

        for (byte b : respHeadBytes) {
            int len = backend.len();
            assertEquals(-1, len);
            ByteArray a = ByteArray.from(b);
            assertEquals(a, backend.feed(a));
        }
        Response resp = backend.getResp();
        {
            assertEquals("HTTP/1.1", resp.version);
            assertEquals(200, resp.statusCode);
            assertEquals("OK", resp.reason);

            assertNull(resp.headers);
        }
        assertTrue(backend.isIdle());
    }

    @Test
    public void noVersionRequest() throws Exception {
        Processor<HttpContext, HttpSubContext> p = new HttpProcessor();
        HttpContext ctx = p.init(address);
        HttpSubContext front = p.initSub(ctx, 0, null);
        front.setParserMode();

        String reqHead = "" +
            "GET /hello/url\r\n" +
            "Host: www.example.com\r\n" +
            "Hello: World\r\n" +
            "\r\n" +
            "";
        byte[] reqHeadBytes = reqHead.getBytes();

        for (byte b : reqHeadBytes) {
            int len = front.len();
            assertEquals(-1, len);
            ByteArray a = ByteArray.from(b);
            ByteArray r = front.feed(a);
            if (r.length() == 1) {
                assertEquals(a, r);
            } else {
                assertEquals(ByteArray.from(("" +
                    "x-forwarded-for: " + forwardedFor + "\r\n" +
                    "x-client-port: " + clientPort + "\r\n" +
                    "").getBytes()).concat(a), r);
            }
        }
        Request req = front.getReq();
        {
            assertEquals("GET", req.method);
            assertEquals("/hello/url", req.uri);
            assertNull(req.version);

            assertEquals("Host", req.headers.get(0).key);
            assertEquals("www.example.com", req.headers.get(0).value);

            assertEquals("Hello", req.headers.get(1).key);
            assertEquals("World", req.headers.get(1).value);
        }
        assertTrue(front.isIdle());
    }

    @Test
    public void noHeaderNorVersionRequest() throws Exception {
        Processor<HttpContext, HttpSubContext> p = new HttpProcessor();
        HttpContext ctx = p.init(null);
        HttpSubContext front = p.initSub(ctx, 0, null);
        front.setParserMode();

        String reqHead = "" +
            "GET /hello/url\r\n" +
            "\r\n" +
            "";
        byte[] reqHeadBytes = reqHead.getBytes();

        for (byte b : reqHeadBytes) {
            int len = front.len();
            assertEquals(-1, len);
            ByteArray a = ByteArray.from(b);
            assertEquals(a, front.feed(a));
        }
        Request req = front.getReq();
        {
            assertEquals("GET", req.method);
            assertEquals("/hello/url", req.uri);
            assertNull(req.version);

            assertNull(req.headers);
        }
        assertTrue(front.isIdle());
    }

    @Test
    public void normalRequest() throws Exception {
        Processor<HttpContext, HttpSubContext> p = new HttpProcessor();
        HttpContext ctx = p.init(address);
        HttpSubContext front = p.initSub(ctx, 0, null);
        front.setParserMode();

        String reqHead = "" +
            "PUT /hello/url HTTP/1.1\r\n" +
            "Host: www.example.com\r\n" +
            "Hello: World\r\n" +
            "Content-Length: 10\r\n" +
            "\r\n" +
            "";
        byte[] reqHeadBytes = reqHead.getBytes();

        for (byte b : reqHeadBytes) {
            int len = front.len();
            assertEquals(-1, len);
            ByteArray a = ByteArray.from(b);
            ByteArray r = front.feed(a);
            if (r.length() == 1) {
                assertEquals(a, r);
            } else {
                assertEquals(ByteArray.from(("" +
                    "x-forwarded-for: " + forwardedFor + "\r\n" +
                    "x-client-port: " + clientPort + "\r\n" +
                    "").getBytes()).concat(a), r);
            }
        }
        Request req = front.getReq();
        {
            assertEquals("PUT", req.method);
            assertEquals("/hello/url", req.uri);
            assertEquals("HTTP/1.1", req.version);

            assertEquals("Host", req.headers.get(0).key);
            assertEquals("www.example.com", req.headers.get(0).value);

            assertEquals("Hello", req.headers.get(1).key);
            assertEquals("World", req.headers.get(1).value);

            assertEquals("Content-Length", req.headers.get(2).key);
            assertEquals("10", req.headers.get(2).value);
        }
        assertEquals(10, front.len());
        front.feed(ByteArray.from("01234567".getBytes()));
        front.feed(ByteArray.from("89".getBytes()));
        assertTrue(front.isIdle());
        req = front.getReq();
        assertEquals(ByteArray.from("0123456789".getBytes()), req.body);
    }

    @Test
    public void normalResponse() throws Exception {
        Processor<HttpContext, HttpSubContext> p = new HttpProcessor();
        HttpContext ctx = p.init(null);
        HttpSubContext backend = p.initSub(ctx, 1, null);
        backend.setParserMode();

        String respHead = "" +
            "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/json\r\n" +
            "Content-Length: 20\r\n" +
            "\r\n";
        byte[] respHeadBytes = respHead.getBytes();

        for (byte b : respHeadBytes) {
            int len = backend.len();
            assertEquals(-1, len);
            ByteArray a = ByteArray.from(b);
            assertEquals(a, backend.feed(a));
        }
        Response resp = backend.getResp();
        {
            assertEquals("HTTP/1.1", resp.version);
            assertEquals(200, resp.statusCode);
            assertEquals("OK", resp.reason);

            assertEquals("Content-Type", resp.headers.get(0).key);
            assertEquals("application/json", resp.headers.get(0).value);

            assertEquals("Content-Length", resp.headers.get(1).key);
            assertEquals("20", resp.headers.get(1).value);
        }
        assertEquals(20, backend.len());
        backend.feed(ByteArray.from("0123456".getBytes()));
        backend.feed(ByteArray.from("78901234".getBytes()));
        backend.feed(ByteArray.from("56789".getBytes()));
        assertTrue(backend.isIdle());
        resp = backend.getResp();
        assertEquals(ByteArray.from("01234567890123456789".getBytes()), resp.body);
    }

    private HttpSubContext chunkRequestNoEnd() throws Exception {
        Processor<HttpContext, HttpSubContext> p = new HttpProcessor();
        HttpContext ctx = p.init(address);
        HttpSubContext front = p.initSub(ctx, 0, null);
        front.setParserMode();

        String reqHead = "" +
            "POST /hello/url HTTP/1.1\r\n" +
            "Host: www.example.com\r\n" +
            "Hello: World\r\n" +
            "Transfer-Encoding: chunked\r\n" +
            "\r\n" +
            "";
        byte[] reqHeadBytes = reqHead.getBytes();

        for (byte b : reqHeadBytes) {
            int len = front.len();
            assertEquals(-1, len);
            ByteArray a = ByteArray.from(b);
            ByteArray r = front.feed(a);
            if (r.length() == 1) {
                assertEquals(a, r);
            } else {
                assertEquals(ByteArray.from(("" +
                    "x-forwarded-for: " + forwardedFor + "\r\n" +
                    "x-client-port: " + clientPort + "\r\n" +
                    "").getBytes()).concat(a), r);
            }
        }
        Request req = front.getReq();
        {
            assertEquals("POST", req.method);
            assertEquals("/hello/url", req.uri);
            assertEquals("HTTP/1.1", req.version);

            assertEquals("Host", req.headers.get(0).key);
            assertEquals("www.example.com", req.headers.get(0).value);

            assertEquals("Hello", req.headers.get(1).key);
            assertEquals("World", req.headers.get(1).value);

            assertEquals("Transfer-Encoding", req.headers.get(2).key);
            assertEquals("chunked", req.headers.get(2).value);
        }
        LinkedHashMap<byte[], ByteArray> chunks = new LinkedHashMap<>() {{
            put("1a  \r\n".getBytes(), ByteArray.from("01234567890123456789012345".getBytes()));
            put("3 ; some-extension\r\n".getBytes(), ByteArray.from("012".getBytes()));
        }};
        for (byte[] chunk : chunks.keySet()) {
            ByteArray content = chunks.get(chunk);
            for (byte b : chunk) {
                int len = front.len();
                assertEquals(-1, len);
                ByteArray a = ByteArray.from(b);
                assertEquals(a, front.feed(a));
            }
            int len = front.len();
            assertEquals(content.length(), len);
            front.feed(content.sub(0, content.length() - 3));
            front.feed(content.sub(content.length() - 3, 3));
            len = front.len();
            assertEquals(-1, len);
            assertEquals(ByteArray.from('\r'), front.feed(ByteArray.from('\r')));
            assertEquals(ByteArray.from('\n'), front.feed(ByteArray.from('\n')));
        }

        byte[] lastChunk = "0\r\n".getBytes();
        for (byte b : lastChunk) {
            int len = front.len();
            assertEquals(-1, len);
            ByteArray a = ByteArray.from(b);
            assertEquals(a, front.feed(a));
        }
        req = front.getReq();
        {
            assertEquals(3, req.chunks.size());

            assertEquals(26, req.chunks.get(0).size);
            assertNull(req.chunks.get(0).extension);
            assertEquals(ByteArray.from("01234567890123456789012345".getBytes()), req.chunks.get(0).content);

            assertEquals(3, req.chunks.get(1).size);
            assertEquals("some-extension", req.chunks.get(1).extension);
            assertEquals(ByteArray.from("012".getBytes()), req.chunks.get(1).content);

            assertEquals(0, req.chunks.get(2).size);
            assertNull(req.chunks.get(2).extension);
            assertNull(req.chunks.get(2).content);
        }

        return front;
    }

    private HttpSubContext chunkResponseNoEnd() throws Exception {
        Processor<HttpContext, HttpSubContext> p = new HttpProcessor();
        HttpContext ctx = p.init(null);
        HttpSubContext backend = p.initSub(ctx, 1, null);
        backend.setParserMode();

        String respHead = "" +
            "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/json\r\n" +
            "Transfer-Encoding: chunked\r\n" +
            "\r\n";
        byte[] respHeadBytes = respHead.getBytes();

        for (byte b : respHeadBytes) {
            int len = backend.len();
            assertEquals(-1, len);
            ByteArray a = ByteArray.from(b);
            assertEquals(a, backend.feed(a));
        }
        Response resp = backend.getResp();
        {
            assertEquals("HTTP/1.1", resp.version);
            assertEquals(200, resp.statusCode);
            assertEquals("OK", resp.reason);

            assertEquals("Content-Type", resp.headers.get(0).key);
            assertEquals("application/json", resp.headers.get(0).value);

            assertEquals("Transfer-Encoding", resp.headers.get(1).key);
            assertEquals("chunked", resp.headers.get(1).value);
        }
        LinkedHashMap<byte[], ByteArray> chunks = new LinkedHashMap<>() {{
            put("1a  \r\n".getBytes(), ByteArray.from("01234567890123456789012345".getBytes()));
            put("3 ; some-extension\r\n".getBytes(), ByteArray.from("012".getBytes()));
        }};
        for (byte[] chunk : chunks.keySet()) {
            ByteArray content = chunks.get(chunk);
            for (byte b : chunk) {
                int len = backend.len();
                assertEquals(-1, len);
                ByteArray a = ByteArray.from(b);
                assertEquals(a, backend.feed(a));
            }
            int len = backend.len();
            assertEquals(content.length(), len);
            backend.feed(content.sub(0, content.length() - 3));
            backend.feed(content.sub(content.length() - 3, 3));
            len = backend.len();
            assertEquals(-1, len);
            assertEquals(ByteArray.from('\r'), backend.feed(ByteArray.from('\r')));
            assertEquals(ByteArray.from('\n'), backend.feed(ByteArray.from('\n')));
        }
        byte[] lastChunkAndEnd = "0\r\n".getBytes();
        for (byte b : lastChunkAndEnd) {
            int len = backend.len();
            assertEquals(-1, len);
            ByteArray a = ByteArray.from(b);
            assertEquals(a, backend.feed(a));
        }
        resp = backend.getResp();
        {
            assertEquals(3, resp.chunks.size());

            assertEquals(26, resp.chunks.get(0).size);
            assertNull(resp.chunks.get(0).extension);
            assertEquals(ByteArray.from("01234567890123456789012345".getBytes()), resp.chunks.get(0).content);

            assertEquals(3, resp.chunks.get(1).size);
            assertEquals("some-extension", resp.chunks.get(1).extension);
            assertEquals(ByteArray.from("012".getBytes()), resp.chunks.get(1).content);

            assertEquals(0, resp.chunks.get(2).size);
            assertNull(resp.chunks.get(2).extension);
            assertNull(resp.chunks.get(2).content);
        }

        return backend;
    }

    @Test
    public void chunkRequest() throws Exception {
        HttpSubContext front = chunkRequestNoEnd();
        byte[] end = "\r\n".getBytes();
        for (byte b : end) {
            int len = front.len();
            assertEquals(-1, len);
            ByteArray a = ByteArray.from(b);
            assertEquals(a, front.feed(a));
        }
        assertTrue(front.isIdle());
    }

    @Test
    public void chunkRequestTrailers() throws Exception {
        HttpSubContext front = chunkRequestNoEnd();
        byte[] trailersAndEnd = ("" +
            "A-Trail: value1\r\n" +
            "B-Trail: value2\r\n" +
            "\r\n" +
            "").getBytes();
        for (byte b : trailersAndEnd) {
            int len = front.len();
            assertEquals(-1, len);
            ByteArray a = ByteArray.from(b);
            assertEquals(a, front.feed(a));
        }
        assertTrue(front.isIdle());
        Request req = front.getReq();
        assertEquals(2, req.trailers.size());
        assertEquals("A-Trail", req.trailers.get(0).key);
        assertEquals("value1", req.trailers.get(0).value);
        assertEquals("B-Trail", req.trailers.get(1).key);
        assertEquals("value2", req.trailers.get(1).value);
    }

    @Test
    public void chunkResponse() throws Exception {
        HttpSubContext backend = chunkResponseNoEnd();
        byte[] lastChunkAndEnd = "\r\n".getBytes();
        for (byte b : lastChunkAndEnd) {
            int len = backend.len();
            assertEquals(-1, len);
            ByteArray a = ByteArray.from(b);
            assertEquals(a, backend.feed(a));
        }
        assertTrue(backend.isIdle());
    }

    @Test
    public void chunkResponseTrailers() throws Exception {
        HttpSubContext backend = chunkResponseNoEnd();
        byte[] trailersAndEnd = ("" +
            "A-Trail: value1\r\n" +
            "B-Trail: value2\r\n" +
            "\r\n" +
            "").getBytes();
        for (byte b : trailersAndEnd) {
            int len = backend.len();
            assertEquals(-1, len);
            ByteArray a = ByteArray.from(b);
            assertEquals(a, backend.feed(a));
        }
        assertTrue(backend.isIdle());
        Response resp = backend.getResp();
        assertEquals(2, resp.trailers.size());
        assertEquals("A-Trail", resp.trailers.get(0).key);
        assertEquals("value1", resp.trailers.get(0).value);
        assertEquals("B-Trail", resp.trailers.get(1).key);
        assertEquals("value2", resp.trailers.get(1).value);
    }
}
