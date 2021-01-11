package vlibbase.impl;

import vlibbase.ConnRef;
import vlibbase.ConnRefPool;
import vproxybase.connection.Connection;
import vproxybase.connection.ConnectionHandler;
import vproxybase.connection.ConnectionHandlerContext;
import vproxybase.connection.NetEventLoop;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.util.LogType;
import vproxybase.util.Logger;
import vproxybase.util.thread.VProxyThread;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;

public class ConnRefPoolImpl implements ConnRefPool {
    private class ConnRefHolder implements ConnectionHandler {
        final Connection conn;
        volatile boolean removed = false;

        private ConnRefHolder(Connection conn) {
            this.conn = conn;
        }

        @Override
        public void readable(ConnectionHandlerContext ctx) {
            // do nothing
        }

        @Override
        public void writable(ConnectionHandlerContext ctx) {
            // do nothing
        }

        @Override
        public void exception(ConnectionHandlerContext ctx, IOException err) {
            Logger.warn(LogType.CONN_ERROR, "pooled connection " + conn + " got exception", err);
        }

        @Override
        public void remoteClosed(ConnectionHandlerContext ctx) {
            ctx.connection.close();
        }

        @Override
        public void closed(ConnectionHandlerContext ctx) {
            connections.remove(this);
        }

        @Override
        public void removed(ConnectionHandlerContext ctx) {
            if (!removed) {
                Logger.error(LogType.IMPROPER_USE, "the pooled connection " + conn + " is removed from loop");
                conn.close();
            }
        }
    }

    private final Options opts;
    private final NetEventLoop loop;
    private final ConcurrentLinkedDeque<ConnRefHolder> connections;

    private boolean isClosed = false;

    public ConnRefPoolImpl(Options opts) {
        this.opts = new Options(opts);
        NetEventLoop loop;
        if (opts.loop == null) {
            SelectorEventLoop sloop;
            try {
                sloop = SelectorEventLoop.open();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            sloop.loop(r -> VProxyThread.create(r, "conn-ref-pool"));
            loop = new NetEventLoop(sloop);
        } else {
            loop = opts.loop;
        }
        this.loop = loop;
        connections = new ConcurrentLinkedDeque<>();
    }

    @Override
    public int count() {
        return connections.size();
    }

    @Override
    public Optional<ConnRef> get() {
        var conn = poll();
        if (conn == null) {
            return Optional.empty();
        }
        return Optional.of(new SimpleConnRef(conn));
    }

    @Override
    public boolean isClosed() {
        return isClosed;
    }

    private Connection poll() {
        var holder = connections.pollFirst();
        if (holder == null) {
            return null;
        }
        holder.removed = true;
        loop.removeConnection(holder.conn);
        return holder.conn;
    }

    @Override
    public void close() {
        if (isClosed) {
            return;
        }
        isClosed = true;
        try {
            loop.getSelectorEventLoop().close();
        } catch (IOException e) {
            Logger.shouldNotHappen("closing event loop failed", e);
        }
    }

    @Override
    public NetEventLoop getLoop() {
        return loop;
    }

    @Override
    public Void receiveTransferredConnection0(ConnRef conn) throws IOException {
        if (isClosed) {
            throw new IllegalStateException("the pool is already closed");
        }
        if (!conn.isTransferring()) {
            throw new IllegalStateException("conn " + conn + " is not transferring");
        }
        if (!conn.isValidRef()) {
            throw new IllegalStateException("conn " + conn + " is not valid");
        }

        if (connections.size() >= opts.maxCount) {
            var polled = poll();
            if (polled != null) {
                polled.close();
            }
        }

        var raw = conn.raw();
        raw.setTimeout(opts.idleTimeout);
        var holder = new ConnRefHolder(raw);
        try {
            loop.addConnection(raw, null, holder);
        } catch (IOException e) {
            Logger.error(LogType.EVENT_LOOP_ADD_FAIL, "adding connection " + conn + " to pool failed", e);
            throw e;
        }

        connections.add(holder);

        return null;
    }
}
