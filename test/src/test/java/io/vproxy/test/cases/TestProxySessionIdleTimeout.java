package io.vproxy.test.cases;

import io.vproxy.base.Config;
import io.vproxy.base.connection.*;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.RingBuffer;
import io.vproxy.base.util.nio.ByteArrayChannel;
import io.vproxy.component.proxy.Session;
import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

import static org.junit.Assert.*;

public class TestProxySessionIdleTimeout {
    static class Fixture implements AutoCloseable {
        final long oldTimestamp = Config.currentTimestamp;
        final TestTunReadFairness.ClockFDs clock = new TestTunReadFairness.ClockFDs();
        final SelectorEventLoop loop = SelectorEventLoop.open(clock);
        final NetEventLoop net = new NetEventLoop(loop);
        final RingBuffer response = RingBuffer.allocate(8);
        final Connection active, passive;
        final TestConnectionReadFairness.Input downstream, upstream;
        final Session session;
        int writeAllowance, exceptions;
        IOException lastException;

        Fixture() throws Exception { this(false); }

        Fixture(boolean connecting) throws Exception {
            Config.currentTimestamp = clock.currentTimeMillis();
            loop.setBeforePoll(() -> 0);
            downstream = new TestConnectionReadFairness.Input(loop, clock, 0) {
                public void onRegister() { loop.selector.registerVirtualWritable(this); }
                public void onRemove() { loop.selector.removeVirtualWritable(this); }
                public int write(ByteBuffer src) {
                    int n = Math.min(writeAllowance, src.remaining());
                    src.position(src.position() + n);
                    writeAllowance -= n;
                    return n;
                }
            };
            upstream = new TestConnectionReadFairness.Input(loop, clock, 8) {
                public boolean isConnected() { return !connecting; }
                public void onRegister() { if (!connecting) super.onRegister(); }
                public int read(ByteBuffer dst) {
                    if (sent == total) {
                        loop.selector.removeVirtualReadable(this);
                        return 0; // Keep-alive upstream; the response has ended without EOF.
                    }
                    int n = Math.min(dst.remaining(), total - sent);
                    dst.put(payload, sent, n);
                    sent += n;
                    if (sent == total) loop.selector.removeVirtualReadable(this);
                    return n;
                }
            };
            var request = RingBuffer.allocate(8);
            active = Connection.wrap(downstream, new ConnectionOpts().setTimeout(60_000), request, response);
            var opts = new ConnectionOpts().setTimeout(60_000).setConnectTimeout(15_000);
            passive = connecting
                ? ConnectableConnection.wrap(upstream, upstream.getRemoteAddress(), opts, response, request)
                : Connection.wrap(upstream, opts, response, request);
            session = new Session(active, passive);
            var handler = new ConnectableConnectionHandler() {
                public void connected(ConnectableConnectionHandlerContext ctx) { }
                public void readable(ConnectionHandlerContext ctx) { }
                public void writable(ConnectionHandlerContext ctx) { }
                public void remoteClosed(ConnectionHandlerContext ctx) { }
                public void exception(ConnectionHandlerContext ctx, IOException e) {
                    ++exceptions;
                    lastException = e;
                    session.close();
                }
                public void closed(ConnectionHandlerContext ctx) { }
                public void removed(ConnectionHandlerContext ctx) { }
            };
            net.addConnection(active, null, handler);
            if (connecting) {
                net.addConnectableConnection((ConnectableConnection) passive, null, handler);
                active.runNoQuickWrite(c -> response.storeBytesFrom(ByteArrayChannel.fromFull(new byte[8])));
            } else {
                net.addConnection(passive, null, handler);
            }
            loop.onePoll();
            assertEquals(8, response.used());
            // Model a downstream that becomes writable only when it can progress.
            loop.selector.removeVirtualWritable(downstream);
        }

        void at(long millis) throws Exception {
            clock.nanos = millis * 1_000_000;
            loop.onePoll();
            // delay() enqueues timer registration for the following tick.
            // Register a re-armed timer before advancing the fixture clock.
            loop.onePoll();
        }

        void writeAt(long millis, int bytes) throws Exception {
            writeAllowance = bytes;
            loop.selector.registerVirtualWritable(downstream);
            clock.nanos = millis * 1_000_000;
            loop.onePoll();
            loop.selector.removeVirtualWritable(downstream);
            loop.onePoll();
        }

        public void close() throws Exception {
            session.close();
            loop.close();
            Config.currentTimestamp = oldTimestamp;
        }
    }

    @Test public void downstreamProgressKeepsQuietUpstreamAlive() throws Exception {
        try (var f = new Fixture()) {
            f.writeAt(40_000, 1);
            f.writeAt(59_000, 1);
            assertEquals(2, f.active.getToRemoteBytes());
            assertEquals(8, f.passive.getFromRemoteBytes());
            f.at(60_001);
            assertEquals("a draining response is still an active relay session", 0, f.exceptions);
            assertFalse(f.session.isClosed());
            assertEquals(6, f.response.used());
            f.at(119_001);
            assertTrue("the original idle deadline still applies after progress stops", f.exceptions > 0);
            assertTrue(f.lastException instanceof SocketTimeoutException);
            assertTrue(f.session.isClosed());
        }
    }

    @Test public void blockedOutputDoesNotKeepQuietUpstreamAlive() throws Exception {
        try (var f = new Fixture()) {
            f.writeAt(59_000, 0);
            assertEquals(0, f.active.getToRemoteBytes());
            f.at(60_001);
            assertEquals(1, f.exceptions);
            assertTrue(f.lastException instanceof SocketTimeoutException);
            assertTrue(f.session.isClosed());
        }
    }

    @Test public void downstreamProgressDoesNotPostponeUpstreamConnectDeadline() throws Exception {
        try (var f = new Fixture(true)) {
            f.writeAt(14_000, 1);
            assertEquals(1, f.active.getToRemoteBytes());
            f.at(15_001);
            assertEquals(1, f.exceptions);
            assertTrue(f.lastException instanceof SocketTimeoutException);
            assertTrue(f.session.isClosed());
        }
    }

    @Test public void upstreamWriteKeepsQuietDownstreamAlive() throws Exception {
        try (var f = new Fixture()) {
            f.clock.nanos = 59_000_000_000L;
            f.passive.runNoQuickWrite(c -> f.active.getInBuffer()
                .storeBytesFrom(ByteArrayChannel.fromFull(new byte[]{1})));
            f.loop.selector.registerVirtualWritable(f.upstream);
            f.loop.onePoll();
            f.loop.selector.removeVirtualWritable(f.upstream);
            assertEquals(1, f.passive.getToRemoteBytes());
            f.at(60_001);
            assertEquals(0, f.exceptions);
            assertFalse(f.session.isClosed());
        }
    }

    @Test public void noProgressStillClosesBothSides() throws Exception {
        try (var f = new Fixture()) {
            f.at(60_001);
            // Both expired timers may already be selected before either closes
            // the session. Cancellation need not remove that selected callback.
            assertTrue(f.exceptions > 0);
            assertTrue(f.lastException instanceof SocketTimeoutException);
            assertTrue(f.session.isClosed());
        }
    }
}
