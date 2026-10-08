package io.vproxy.test.cases;

import io.vproxy.base.Config;
import io.vproxy.base.connection.*;
import io.vproxy.base.http.*;
import io.vproxy.base.protocol.*;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.*;
import io.vproxy.base.util.nio.ByteArrayChannel;
import io.vproxy.base.util.ringbuffer.AbstractWrapByteBufferRingBuffer;
import io.vproxy.base.util.ringbuffer.ByteBufferRingBuffer;
import io.vproxy.vproxyx.websocks.WebSocksProtocolHandler;
import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.Assert.*;

public class TestHttpErrorLifecycle {
    static class Fixture implements AutoCloseable {
        final long timestamp = Config.currentTimestamp;
        final TestTunReadFairness.ClockFDs clock = new TestTunReadFairness.ClockFDs();
        final SelectorEventLoop loop = SelectorEventLoop.open(clock);
        final NetEventLoop net = new NetEventLoop(loop);
        final ByteArrayOutputStream sent = new ByteArrayOutputStream();
        final TestConnectionReadFairness.Input fd;
        final Connection connection;
        boolean paused = true;
        Fixture() throws Exception { this(RingBuffer.allocate(8)); }
        Fixture(RingBuffer output) throws Exception {
            Config.currentTimestamp = clock.currentTimeMillis();
            loop.setBeforePoll(() -> 0);
            fd = new TestConnectionReadFairness.Input(loop, clock, 0) {
                @Override public void onRegister() { }
                @Override public int write(ByteBuffer src) {
                    if (paused) { loop.selector.removeVirtualWritable(this); return 0; }
                    int n = Math.min(7, src.remaining());
                    for (int i = 0; i < n; i++) Fixture.this.sent.write(src.get());
                    return n;
                }
            };
            connection = Connection.wrap(fd, new ConnectionOpts(), RingBuffer.allocate(1024), output);
        }
        <T> ProtocolHandlerContext<T> attach(ProtocolHandler<T> handler) throws Exception {
            var ctx = new ProtocolHandlerContext<T>(connection.id(), connection, net, handler);
            handler.init(ctx);
            net.addConnection(connection, handler, new ProtocolConnectionHandler(ctx));
            return ctx;
        }
        <T> void input(ProtocolHandlerContext<T> ctx, String data) {
            var channel = ByteArrayChannel.fromFull(data.getBytes(StandardCharsets.UTF_8));
            while (channel.used() != 0 && !connection.isClosed()) {
                int n = ctx.inBuffer.storeBytesFrom(channel);
                if (n == 0) throw new AssertionError("handler stopped consuming input");
                ctx.handler.readable(ctx);
                if (ctx.isClosing()) break;
            }
        }
        String finish() throws Exception {
            assertFalse("do not close before blocked response is sent", connection.isClosed());
            paused = false;
            loop.selector.registerVirtualWritable(fd);
            for (int i = 0; i < 2000 && !connection.isClosed(); i++) loop.onePoll();
            assertTrue("close once output is fully drained", connection.isClosed());
            assertFalse(fd.isOpen());
            return sent.toString(StandardCharsets.UTF_8);
        }
        public void close() throws Exception { connection.close(); loop.close(); Config.currentTimestamp = timestamp; }
    }
    static HttpProtocolHandler handler(HttpReqParser.Params params) {
        return new HttpProtocolHandler(params) {
            @Override protected void request(ProtocolHandlerContext<HttpContext> ctx) {
                throw new AssertionError("unexpected request callback");
            }
        };
    }
    static void contentLength(String response) {
        int split = response.indexOf("\r\n\r\n");
        assertTrue(split > 0);
        String field = response.substring(0, split).lines().filter(s -> s.startsWith("Content-Length:")).findFirst().orElseThrow();
        assertEquals(Integer.parseInt(field.substring("Content-Length:".length()).trim()),
            response.substring(split + 4).getBytes(StandardCharsets.UTF_8).length);
    }
    @Test public void parseErrorClosesAfterPartialWritesAndDoesNotReuseTheParser() throws Exception {
        try (var f = new Fixture()) {
            var ctx = f.attach(handler(new HttpReqParser.Params().setHeadersOnly(true)));
            f.input(ctx, "GET / HTTP/1.1\r\nContent-Length: bad\r\n\r\n");
            assertTrue(ctx.data.terminated); assertNull(ctx.data.parser); assertNull(ctx.data.result);
            assertTrue(ctx.isClosing());
            ctx.handler.readable(ctx); // repeated readiness must not enter state -1
            ctx.write("must not be sent".getBytes(StandardCharsets.UTF_8));
            String response = f.finish();
            assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"));
            assertTrue(response.contains("Connection: close\r\n"));
            assertFalse(response.contains("must not be sent"));
            contentLength(response);
        }
    }
    @Test public void headerLimitReturns431AndReleasesTheBuilder() throws Exception {
        try (var f = new Fixture()) {
            var ctx = f.attach(handler(new HttpReqParser.Params().setHeadersOnly(true).setMaxHeaderLineBytes(16)));
            f.input(ctx, "GET / HTTP/1.1\r\nX: " + "a".repeat(100) + "\r\n\r\n");
            assertNull(ctx.data.parser);
            String response = f.finish();
            assertTrue(response.startsWith("HTTP/1.1 431 "));
            assertTrue(response.length() < 512);
            contentLength(response);
        }
    }
    @Test public void initialLineLimitReturns414() throws Exception {
        try (var f = new Fixture()) {
            var ctx = f.attach(handler(new HttpReqParser.Params().setHeadersOnly(true).setMaxInitialLineBytes(16)));
            f.input(ctx, "GET /" + "a".repeat(30) + " HTTP/1.1\r\n\r\n");
            assertTrue(f.finish().startsWith("HTTP/1.1 414 "));
        }
    }
    @Test public void applicationErrorAfterSuccessfulParsingStillReturns400() throws Exception {
        try (var f = new Fixture()) {
            var h = new HttpProtocolHandler(new HttpReqParser.Params().setHeadersOnly(true)) {
                protected void request(ProtocolHandlerContext<HttpContext> ctx) {
                    assertNull(ctx.data.parser);
                    sendError(ctx, "unsupported request");
                }
            };
            var ctx = f.attach(h);
            f.input(ctx, "GET / HTTP/1.1\r\n\r\n");
            assertTrue(f.finish().startsWith("HTTP/1.1 400 "));
        }
    }
    @Test public void errorResponseEscapesUntrustedTextAndUsesEncodedLength() throws Exception {
        try (var f = new Fixture()) {
            var ctx = f.attach(handler(new HttpReqParser.Params().setHeadersOnly(true)));
            f.input(ctx, "GET / HTTP/1.1\r\nContent-Length: <tag>&é\r\n\r\n");
            String response = f.finish();
            assertFalse(response.contains("<tag>"));
            assertTrue(response.contains("&lt;tag&gt;&amp;"));
            contentLength(response);
        }
    }
    @Test public void nestedContextFlushesTheParentsWholeQueue() throws Exception {
        try (var f = new Fixture()) {
            var parent = f.attach(handler(new HttpReqParser.Params().setHeadersOnly(true)));
            var child = new SubProtocolHandlerContext<HttpContext>(parent, "child", f.connection, f.net, parent.handler);
            child.write(ByteArray.from("one-".repeat(80)));
            child.write("two".repeat(50).getBytes(StandardCharsets.UTF_8));
            child.closeAfterFlush();
            assertTrue(parent.isClosing()); assertTrue(child.isClosing());
            assertEquals("one-".repeat(80) + "two".repeat(50), f.finish());
        }
    }
    @Test public void websocksActualIngressEnforcesTheHeaderBudgetBeforeAuth() throws Exception {
        try (var f = new Fixture()) {
            var ctx = f.attach(new WebSocksProtocolHandler(Map.of("offline", "unused"), null, false, null, null));
            f.input(ctx, "GET / HTTP/1.1\r\nX: " + "a".repeat(32768));
            assertTrue(ctx.isClosing());
            assertTrue(f.finish().startsWith("HTTP/1.1 431 "));
        }
    }
    @Test public void stagedTransformOutputIsNotMistakenForAFlushedResponse() throws Exception {
        var wrap = new AbstractWrapByteBufferRingBuffer(RingBuffer.allocate(8)) {
            boolean ready;
            protected void handlePlainBuffer(ByteBufferEx plain, boolean[] errored, IOException[] error) {
                if (!ready || plain.position() == plain.limit()) return;
                var bytes = ByteBuffer.allocate(plain.limit() - plain.position());
                bytes.put(plain.realBuffer()).flip();
                recordIntermediateBuffer(bytes);
                transferring = true;
            }
            void release() {
                ready = true;
                // writeTo calls generalWrap even when its visible output was initially empty.
            }
        };
        try (var f = new Fixture(wrap)) {
            var ctx = f.attach(handler(new HttpReqParser.Params().setHeadersOnly(true)));
            ctx.write("pending".getBytes(StandardCharsets.UTF_8));
            assertEquals(0, wrap.used()); assertTrue(wrap.hasPendingWrite());
            ctx.closeAfterFlush();
            assertFalse(f.connection.isClosed());
            wrap.release();
            f.paused = false;
            wrap.writeTo(f.fd);
            new ProtocolConnectionHandler(ctx).writable(null);
            f.loop.onePoll();
            assertTrue(f.connection.isClosed());
            assertEquals("pending", f.sent.toString(StandardCharsets.UTF_8));
        }
    }
}
