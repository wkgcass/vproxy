package io.vproxy.base.component.pool;

import io.vproxy.base.connection.*;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.anno.ThreadSafe;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Objects;
import java.util.function.Consumer;

public class ConnectionPool {
    static class ConnWrap {
        final ConnectableConnection conn;
        boolean isHandshaking = true;

        ConnWrap(ConnectableConnection conn) {
            this.conn = conn;
        }
    }

    class PoolConnHandler implements ConnectableConnectionHandler {
        @Override
        public void connected(ConnectableConnectionHandlerContext ctx) {
            // ignore the event
        }

        @Override
        public void readable(ConnectionHandlerContext ctx) {
            assert Logger.lowLevelDebug("the pooled connection " + ctx.connection + " is receiving data");
            handler.keepaliveReadable((ConnectableConnection) ctx.connection);
            if (!ctx.connection.isClosed() && ctx.connection.getInBuffer().used() != 0) {
                Logger.error(LogType.IMPROPER_USE, "the user code did not consume all data in the inBuffer");
                ctx.connection.close(true);
            }
        }

        @Override
        public void writable(ConnectionHandlerContext ctx) {
            // ignore the event
        }

        @Override
        public void exception(ConnectionHandlerContext ctx, IOException err) {
            Logger.error(LogType.CONN_ERROR, "pooled connection " + ctx.connection + " got exception", err);
        }

        @Override
        public void remoteClosed(ConnectionHandlerContext ctx) {
            ctx.connection.close();
            closed(ctx);
        }

        @Override
        public void closed(ConnectionHandlerContext ctx) {
            Logger.warn(LogType.CONN_ERROR, "pooled connection " + ctx.connection + " closed");
            removeConnection(ctx.connection);
        }

        @Override
        public void removed(ConnectionHandlerContext ctx) {
            // maybe it's removed from the pool
        }
    }

    private volatile boolean isClosed = false;
    private final NetEventLoop loop;
    private final ConnectionPoolHandler handler;
    private final LinkedList<ConnWrap> connections = new LinkedList<>();
    private final int capacity;
    private final int highWatermark;
    private final int lowWatermark;

    private final PoolConnHandler poolConnHandler = new PoolConnHandler();
    private boolean isPendingProviding = false;
    private boolean needToFillByWatermark = false;

    private final int idleTimeoutInPool;
    private final int idleTimeoutOutOfPool;

    public ConnectionPool(NetEventLoop loop, ConnectionPoolParams params) {
        this(loop, params, null);
    }

    public ConnectionPool(NetEventLoop loop, ConnectionPoolParams params,
                          ConnectionPoolHandlerProvider handlerProvider) {
        if (handlerProvider == null) {
            if (params.lowWatermark != 0 || params.highWatermark != 0 || params.keepaliveInterval >= 0)
                throw new IllegalArgumentException(
                    "low watermark and high watermark must be 0, keepaliveInterval must be -1 if handlerProvider not specified");
            handlerProvider = _ -> SimpleConnectionPoolHandler.get();
        }
        this.loop = loop;
        if (params.capacity <= 0)
            throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = params.capacity;
        var highWatermark = params.highWatermark;
        if (highWatermark < 0) {
            highWatermark = capacity;
        }
        this.highWatermark = highWatermark;
        var lowWatermark = params.lowWatermark;
        if (lowWatermark < 0) {
            lowWatermark = capacity;
        }
        this.lowWatermark = lowWatermark;
        this.idleTimeoutInPool = params.idleTimeoutInPool;
        this.idleTimeoutOutOfPool = params.idleTimeoutOutOfPool;
        this.handler = handlerProvider.provide(new ConnectionPoolHandlerProvider.ProvideParams(
            new PoolCallback(this)
        ));

        fill();
        // run keepalive
        if (params.keepaliveInterval > 0) {
            loop.getSelectorEventLoop().period(params.keepaliveInterval, this::keepalive);
        }
    }

    public NetEventLoop getLoop() {
        return loop;
    }

    private void fill() {
        if (isClosed) {
            assert Logger.lowLevelDebug("the pool is already closed, do not fill");
            return;
        }
        if (!needToFillByWatermark) {
            if (connections.size() <= lowWatermark && connections.size() <= highWatermark) {
                assert Logger.lowLevelDebug("pooled connections <= low watermark and <= high watermark, do fill");
                needToFillByWatermark = true;
            }
            if (!needToFillByWatermark) {
                assert Logger.lowLevelDebug("no need to fill");
                return;
            }
        }
        assert Logger.lowLevelDebug("try to fill the pool");
        if (connections.size() >= capacity) {
            assert Logger.lowLevelDebug("the pool is full now, do not create more");
            return;
        }
        if (isPendingProviding) {
            assert Logger.lowLevelDebug("the pool is pending providing, so do nothing for now");
            return;
        }

        ConnectableConnection conn = handler.provide(loop);
        if (conn == null) {
            assert Logger.lowLevelDebug("the user code refuse to provide a connection to the pool");
        } else if (conn.getEventLoop() == null) {
            Logger.error(LogType.IMPROPER_USE, "user code did not register the conn to event loop");
            conn.close(true);
        } else if (conn.getEventLoop() != loop) {
            Logger.error(LogType.IMPROPER_USE, "user code did not register the conn to the pool's event loop");
            conn.close(true);
        } else {
            assert Logger.lowLevelDebug("new connection provided, add to pool");
            if (idleTimeoutInPool > 0) {
                conn.setTimeout(idleTimeoutInPool);
            }
            ConnWrap w = new ConnWrap(conn);
            _add(w);
        }

        // fix the delay to 1 second, the delay is not important
        // we just don't want to make too many connections at the same time
        loop.getSelectorEventLoop().delay(1_000, () -> {
            isPendingProviding = false;
            assert Logger.lowLevelDebug("pool delay triggers");
            fill();
        });
        isPendingProviding = true;
    }

    private void _add(ConnWrap w) {
        connections.add(w);
        if (connections.size() >= highWatermark) {
            assert Logger.lowLevelDebug("pooled connections >= high watermark, stop filling");
            needToFillByWatermark = false;
        }
    }

    void handshakeDone(ConnectableConnection conn) {
        Logger.trace(LogType.ALERT, "handshake done for pooled connection: " + conn);
        connections.stream().filter(w -> w.conn.equals(conn)).forEach(this::handshakeDone);
    }

    private void handshakeDone(ConnWrap w) {
        w.isHandshaking = false;
        if (w.conn.getEventLoop() != null) {
            loop.removeConnection(w.conn);
        }

        try {
            loop.addConnectableConnection(w.conn, null, poolConnHandler);
        } catch (IOException e) {
            Logger.error(LogType.EVENT_LOOP_ADD_FAIL, "register connection with poolConnHandler failed", e);
            removeConnection(w.conn);
        }
    }

    void removeConnection(Connection conn) {
        assert Logger.lowLevelDebug("connection removed: " + conn);
        // remove from pool
        connections.removeIf(w -> w.conn.equals(conn));
        // close the conn and remove from loop (will is done by the connection lib)
        conn.close();
        fill();
    }

    private void keepalive() {
        for (ConnWrap w : connections) {
            if (w.isHandshaking)
                continue;
            assert Logger.lowLevelDebug("try to run keepalive for " + w.conn);
            handler.keepalive(w.conn);
        }
    }

    @ThreadSafe
    public void get(SelectorEventLoop callerLoop, Consumer<ConnectableConnection> cb) {
        loop.getSelectorEventLoop().runOnLoop(() -> {

            // here is in the connection pool event loop

            ConnWrap firstPolled = connections.poll();
            ConnWrap w = firstPolled;

            if (w == null) { // the pool is empty
                callerLoop.runOnLoop(() -> cb.accept(null));

                fill(); // should fill the pool
                return;
            }

            while (true) {
                if (w.isHandshaking) {
                    // still handshaking, we should add it back to the list
                    connections.add(w); // add to tail
                    w = connections.poll(); // retrieve from head

                    assert w != null; // it's definitely not null because we just added one element
                    if (w.equals(firstPolled)) {
                        assert Logger.lowLevelDebug("we got the first polled connection, " +
                                                    "which means no valid connections in the pool for now");
                        connections.add(w); // we should add it back

                        callerLoop.runOnLoop(() -> cb.accept(null));
                        break;
                    }
                    continue;
                }
                // otherwise it's ready to use

                ConnWrap foo = w; // use a new variable just to let the lambda capture
                loop.removeConnection(w.conn);
                if (idleTimeoutOutOfPool > 0) {
                    w.conn.setTimeout(idleTimeoutOutOfPool);
                }
                Logger.alert("pooled connection retrieved: " + w.conn);
                // sync event loop info into memory to avoid some corner error
                // the connection is removed from loop of pool on loop thread
                // and will be added to another loop on another thread
                // the internal fields may not have been synced to memory
                // so we sync cache manually
                Utils.syncCpuCacheAndMemory();
                callerLoop.runOnLoop(() -> cb.accept(foo.conn));
                break;
            }

            fill(); // should try to fill the pool, checking will be handled in the method
        });
    }

    @ThreadSafe
    public void store(ConnectableConnection conn) {
        Objects.requireNonNull(conn);
        if (isClosed) {
            assert Logger.lowLevelDebug("the pool is already closed, cannot store");
            conn.close();
            return;
        }
        if (conn.getEventLoop() != null) {
            assert Logger.lowLevelDebug("already in loop, must be removed before adding ...");
            conn.getEventLoop().removeConnection(conn);
        }
        if (conn.isClosed()) {
            assert Logger.lowLevelDebug("connection is closed, cannot add");
            return;
        }
        loop.getSelectorEventLoop().runOnLoop(() -> {
            if (isClosed) {
                assert Logger.lowLevelDebug("the pool is already closed, cannot store");
                conn.close();
                return;
            }
            if (connections.size() >= capacity) {
                assert Logger.lowLevelDebug("the pool is full now, cannot add");
                conn.close();
                return;
            }
            if (conn.isClosed()) {
                assert Logger.lowLevelDebug("connection is closed, cannot add");
                return;
            }
            var w = new ConnWrap(conn);
            _add(w);
            handshakeDone(w);
        });
    }

    @ThreadSafe
    public void close() {
        if (isClosed) {
            return;
        }
        isClosed = true;
        if (loop.getSelectorEventLoop().isClosed()) {
            for (var c : connections) {
                c.conn.close();
            }
        } else {
            loop.getSelectorEventLoop().runOnLoop(() -> {
                for (var c : connections) {
                    c.conn.close();
                }
            });
        }
    }
}
