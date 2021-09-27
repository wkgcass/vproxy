package io.vproxy.pool;

import io.vproxy.base.connection.*;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.anno.ThreadSafe;

import java.io.IOException;
import java.util.LinkedList;
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
            if (ctx.connection.getInBuffer().used() != 0) {
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

    private final NetEventLoop loop;
    private final ConnectionPoolHandler handler;
    private final LinkedList<ConnWrap> connections = new LinkedList<>();
    private final int capacity;

    private final PoolConnHandler poolConnHandler = new PoolConnHandler();
    private boolean isPendingProviding = false;

    public ConnectionPool(NetEventLoop loop, ConnectionPoolHandlerProvider handlerProvider, int capacity) {
        this.loop = loop;
        this.capacity = capacity;
        this.handler = handlerProvider.provide(new PoolCallback(this));

        fill();
        // run keepalive for every 15 seconds
        loop.getSelectorEventLoop().period(15_000, this::keepalive);
    }

    private void fill() {
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
        } else {
            assert Logger.lowLevelDebug("new connection provided, add to pool");
            ConnWrap w = new ConnWrap(conn);
            connections.add(w);
        }

        // fix the delay to 1 second, the delay is not important
        // we just don't want to make too much connections at the same time
        loop.getSelectorEventLoop().delay(1_000, () -> {
            isPendingProviding = false;
            assert Logger.lowLevelDebug("pool delay triggers");
            fill();
        });
        isPendingProviding = true;
    }

    void handshakeDone(ConnectableConnection conn) {
        Logger.trace(LogType.ALERT, "handshake done for pooled connection: " + conn);
        connections.stream().filter(w -> w.conn.equals(conn)).forEach(this::handshakeDone);
    }

    private void handshakeDone(ConnWrap w) {
        w.isHandshaking = false;
        loop.removeConnection(w.conn);

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
}
