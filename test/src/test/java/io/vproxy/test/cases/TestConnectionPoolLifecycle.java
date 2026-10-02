package io.vproxy.test.cases;

import io.vproxy.base.Config;
import io.vproxy.base.component.pool.*;
import io.vproxy.base.connection.*;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.RingBuffer;
import io.vproxy.base.util.time.TimeQueue;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class TestConnectionPoolLifecycle {
    static class Fixture implements AutoCloseable {
        final long previousTimestamp = Config.currentTimestamp;
        final TestTunReadFairness.ClockFDs clock = new TestTunReadFairness.ClockFDs();
        final SelectorEventLoop loop = SelectorEventLoop.open(clock);
        final NetEventLoop net = new NetEventLoop(loop);
        final List<ConnectableConnection> created = new ArrayList<>();
        final ConnectionPool pool;
        PoolCallback callback;
        int provides, keepalives;
        boolean closeDuringKeepalive;

        Fixture() throws Exception { this(false, false); }

        Fixture(boolean closeDuringProvide, boolean errorDuringProvide) throws Exception {
            Config.currentTimestamp = 0;
            loop.setBeforePoll(() -> 0);
            pool = new ConnectionPool(net, new ConnectionPoolParams().setCapacity(2)
                .setLowWatermark(0).setHighWatermark(0).setKeepaliveInterval(100), params -> {
                    callback = params.poolCallback();
                    return new ConnectionPoolHandler() {
                        public ConnectableConnection provide(NetEventLoop eventLoop) {
                            ++provides;
                            // Bound the old implementation's synchronous recursion.
                            if (provides > 1) return null;
                            try {
                                var conn = connection();
                                if (closeDuringProvide) owner(callback).close();
                                if (errorDuringProvide) callback.connectionError(conn);
                                return conn;
                            } catch (Exception e) { throw new AssertionError(e); }
                        }
                        public void keepaliveReadable(ConnectableConnection conn) { }
                        public void keepalive(ConnectableConnection conn) {
                            ++keepalives;
                            if (closeDuringKeepalive) pool.close();
                        }
                    };
                });
            if (!closeDuringProvide && !errorDuringProvide) callback.handshakeDone(created.getFirst());
        }

        static ConnectionPool owner(PoolCallback callback) throws Exception {
            var field = PoolCallback.class.getDeclaredField("pool"); field.setAccessible(true);
            return (ConnectionPool) field.get(callback);
        }

        ConnectableConnection connection() throws IOException {
            var fd = new TestConnectionReadFairness.Input(loop, clock, 0) {
                public void onRegister() { } // idle transport, no EOF readiness
                public int read(java.nio.ByteBuffer dst) { return 0; }
            };
            var conn = ConnectableConnection.wrap(fd, fd.getRemoteAddress(), new ConnectionOpts(),
                RingBuffer.allocate(128), RingBuffer.allocate(128));
            net.addConnectableConnection(conn, null, new ConnectableConnectionHandler() {
                public void connected(ConnectableConnectionHandlerContext ctx) { }
                public void readable(ConnectionHandlerContext ctx) { }
                public void writable(ConnectionHandlerContext ctx) { }
                public void exception(ConnectionHandlerContext ctx, IOException e) { }
                public void remoteClosed(ConnectionHandlerContext ctx) { }
                public void closed(ConnectionHandlerContext ctx) { }
                public void removed(ConnectionHandlerContext ctx) { }
            });
            created.add(conn);
            return conn;
        }

        int retainedConnections() throws Exception {
            var field = ConnectionPool.class.getDeclaredField("connections"); field.setAccessible(true);
            return ((List<?>) field.get(pool)).size();
        }

        boolean timersEmpty() throws Exception {
            var field = SelectorEventLoop.class.getDeclaredField("timeQueue"); field.setAccessible(true);
            return ((TimeQueue<?>) field.get(loop)).isEmpty();
        }

        void pollAt(int milliseconds) {
            clock.nanos = milliseconds * 1_000_000L;
            loop.onePoll(); loop.onePoll(); // install/cancel tasks and deferred batches
        }

        public void close() throws Exception {
            try {
                pool.close();
                for (var conn : created) conn.close();
                if (!loop.isClosed()) loop.close();
            } finally { Config.currentTimestamp = previousTimestamp; }
        }
    }

    @Test public void closeStopsKeepaliveAndFutureFill() throws Exception {
        try (var f = new Fixture()) {
            f.pollAt(0); f.pollAt(100); assertEquals(1, f.keepalives);
            f.pool.close(); f.pollAt(1200); f.pollAt(2400);
            assertEquals("closed pool must not send keepalives", 1, f.keepalives);
            assertEquals(1, f.provides);
            assertTrue("closed pool must not remain rooted by timers", f.timersEmpty());
        }
    }

    @Test public void closeClearsOwnedConnectionsAndIsIdempotent() throws Exception {
        try (var f = new Fixture()) {
            var second = f.connection(); f.pool.store(second);
            assertEquals(2, f.retainedConnections());
            f.pool.close(); f.pool.close();
            assertTrue(f.created.stream().allMatch(Connection::isClosed));
            assertEquals("closed pool must release its connection references", 0, f.retainedConnections());
        }
    }

    @Test public void closeBeforeTimerInstallationLeavesNoTimers() throws Exception {
        try (var f = new Fixture()) {
            f.pool.close(); f.pollAt(0); f.pollAt(1200);
            assertTrue(f.timersEmpty()); assertEquals(0, f.keepalives);
        }
    }

    @Test public void closedPoolGetCompletesWithNull() throws Exception {
        try (var f = new Fixture()) {
            f.pool.close(); var result = new AtomicReference<ConnectableConnection>(); int[] calls = {0};
            f.pool.get(f.loop, conn -> { ++calls[0]; result.set(conn); });
            assertEquals(1, calls[0]); assertNull("closed pool cannot hand off a closed transport", result.get());
        }
    }

    @Test public void providerClosingPoolCannotAddReturnedConnectionOrTimers() throws Exception {
        try (var f = new Fixture(true, false)) {
            assertTrue(f.created.getFirst().isClosed());
            assertEquals(0, f.retainedConnections());
            f.pollAt(0); f.pollAt(1200); assertTrue(f.timersEmpty());
        }
    }

    @Test public void closeDuringKeepaliveDoesNotVisitRemainingClosedConnections() throws Exception {
        try (var f = new Fixture()) {
            f.pool.store(f.connection()); f.pollAt(0); f.closeDuringKeepalive = true; f.pollAt(100);
            assertEquals(1, f.keepalives); assertEquals(0, f.retainedConnections());
            assertTrue(f.created.stream().allMatch(Connection::isClosed));
            f.pollAt(1200); assertTrue(f.timersEmpty());
        }
    }

    @Test public void errorCallbackDuringProvideCannotReenterFill() throws Exception {
        try (var f = new Fixture(false, true)) {
            assertEquals("one synchronous provide attempt", 1, f.provides);
            assertTrue(f.created.getFirst().isClosed());
        }
    }

    @Test public void closeDoesNotCloseAlreadyHandedOffConnection() throws Exception {
        try (var f = new Fixture()) {
            var result = new AtomicReference<ConnectableConnection>(); f.pool.get(f.loop, result::set);
            assertSame(f.created.getFirst(), result.get()); assertFalse(result.get().isClosed());
            f.pool.close(); assertFalse("caller now owns the transport", result.get().isClosed());
        }
    }

    @Test public void closeAfterOwnerLoopClosedStillClearsConnections() throws Exception {
        try (var f = new Fixture()) {
            f.loop.close(); f.pool.close();
            assertTrue(f.created.getFirst().isClosed()); assertEquals(0, f.retainedConnections());
        }
    }
}
