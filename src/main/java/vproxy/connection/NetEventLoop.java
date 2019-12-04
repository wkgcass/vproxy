package vproxy.connection;

import vfd.*;
import vproxy.app.Config;
import vproxy.selector.Handler;
import vproxy.selector.HandlerContext;
import vproxy.selector.SelectorEventLoop;
import vproxy.selector.wrap.VirtualFD;
import vproxy.util.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.channels.ClosedChannelException;

public class NetEventLoop {
    private static final HandlerForTCPServer handlerForTPCServer = new HandlerForTCPServer();
    private static final HandlerForConnection handlerForConnection = new HandlerForConnection();
    private static final HandlerForConnectableConnection handlerForConnectableConnection = new HandlerForConnectableConnection();

    private final SelectorEventLoop selectorEventLoop;

    public NetEventLoop(SelectorEventLoop selectorEventLoop) {
        this.selectorEventLoop = selectorEventLoop;
    }

    public SelectorEventLoop getSelectorEventLoop() {
        return selectorEventLoop;
    }

    @ThreadSafe
    public void addServer(ServerSock server, Object attachment, ServerHandler handler) throws IOException {
        // synchronize in case the fields being inconsistent
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (server) {
            if (server.isClosed())
                throw new ClosedChannelException();
            if (server._eventLoop != null)
                throw new IOException("serverSock already registered to a event loop");
            server._eventLoop = this;
            selectorEventLoop.add(server.channel, EventSet.read(),
                new ServerHandlerContext(this, server, attachment, handler),
                handlerForTPCServer);
        }
    }

    @ThreadSafe
    public void removeServer(ServerSock server) {
        // event loop in server object will be set to null in remove event
        selectorEventLoop.remove(server.channel);
    }

    private void doAddConnection(Connection connection, EventSet ops, ConnectionHandlerContext att, Handler<SocketFD> handler) throws IOException {
        // synchronize connection
        // to prevent inner fields being inconsistent
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (connection) {
            if (connection.isClosed())
                throw new ClosedChannelException();
            // then, the connection will not be closed until added into the event loop
            if (connection.getEventLoop() != null)
                throw new IOException("connection " + connection + " already registered to a event loop");

            connection.setEventLoopRelatedFields(this, att);
            try {
                selectorEventLoop.add(connection.channel, ops, att, handler);
            } catch (IOException e) {
                // remove the eventLoop ref if adding failed
                connection.releaseEventLoopRelatedFields();
                throw e;
            }
        }
        // now the connection is added into event loop
        // we set the close timer
        selectorEventLoop.runOnLoop(() -> NetEventLoopUtils.resetCloseTimeout(att));
    }

    @ThreadSafe
    public void addConnection(Connection connection, Object attachment, ConnectionHandler handler) throws IOException {
        EventSet ops = EventSet.none();
        if (connection.getInBuffer().free() > 0) {
            ops = ops.combine(EventSet.read());
        }
        if (connection.getOutBuffer().used() > 0) {
            ops = ops.combine(EventSet.write());
        }

        doAddConnection(connection, ops,
            new ConnectionHandlerContext(this, connection, attachment, handler),
            handlerForConnection);
    }

    // this method is for both server connection and connectable connection
    @ThreadSafe
    public void removeConnection(Connection connection) {
        assert Logger.lowLevelDebug("removing connection from loop: " + connection);
        // event loop in connection object will be set to null in remove event
        selectorEventLoop.remove(connection.channel);
        // clear timeout
        if (connection.closeTimeout != null) {
            assert Logger.lowLevelDebug("cancel the close timeout: " + connection);
            connection.closeTimeout.cancel();
            connection.closeTimeout = null;
        }
    }

    @ThreadSafe
    public void addConnectableConnection(ConnectableConnection connection, Object attachment, ConnectableConnectionHandler handler) throws IOException {
        boolean fireConnected = false; // whether to fire `connected` event after registering
        // the connection might already be connected
        // so fire the event to let handler know

        EventSet ops;
        if ((connection.channel).isConnected()) {
            fireConnected = true;

            ops = EventSet.none();
            if (connection.getInBuffer().free() > 0)
                ops = ops.combine(EventSet.read());
            if (connection.getOutBuffer().used() > 0)
                ops = ops.combine(EventSet.write());
        } else {
            ops = EventSet.write();
        }

        ConnectableConnectionHandlerContext ctx = new ConnectableConnectionHandlerContext(this, connection, attachment, handler);

        doAddConnection(connection, ops, ctx, handlerForConnectableConnection);

        if (fireConnected) {
            try {
                handler.connected(ctx);
            } catch (Throwable t) {
                Logger.error(LogType.IMPROPER_USE, "the connected callback got exception", t);
            }
        }
    }
}

class HandlerForTCPServer implements Handler<ServerSocketFD> {
    @Override
    public void accept(HandlerContext<ServerSocketFD> ctx) {
        ServerHandlerContext sctx = (ServerHandlerContext) ctx.getAttachment();

        ServerSocketFD server = ctx.getChannel();
        SocketFD sock;
        try {
            sock = server.accept();
        } catch (IOException e) {
            sctx.handler.acceptFail(sctx, e);
            return;
        }
        if (sock == null) {
            assert Logger.lowLevelDebug("no socket yet, ignore this event");
            return;
        }
        Tuple<RingBuffer, RingBuffer> ioBuffers = sctx.handler.getIOBuffers(sock);
        if (ioBuffers == null) { // the user code may return null if refuse to accept
            try {
                sock.close(); // let's close the connection
            } catch (IOException e) {
                Logger.shouldNotHappen("close the unaccepted connection failed: " + e);
            }
        } else {
            Connection conn;
            try {
                conn = new Connection(sock,
                    (InetSocketAddress) sock.getRemoteAddress(),
                    (InetSocketAddress) sock.getLocalAddress(),
                    sctx.handler.connectionOpts(),
                    ioBuffers.left, ioBuffers.right);
            } catch (IOException e) {
                if ("Invalid argument".equals(e.getMessage())) {
                    // the connection might already closed when reaches here
                    // the native impl will throw `Invalid argument`
                    // in this case, no need to log an error message
                    assert Logger.lowLevelDebug("creating Connection object for " + sock + " failed");
                } else {
                    Logger.error(LogType.CONN_ERROR, "creating Connection object for " + sock + " failed: " + e);
                }
                assert Logger.printStackTrace(e);
                // should rollback
                try {
                    sock.close();
                } catch (IOException e1) {
                    Logger.shouldNotHappen("failed to close the sock " + sock + " after failed creating Connection object", e1);
                }
                return;
            }
            conn.addNetFlowRecorder(sctx.server);
            sctx.handler.connection(sctx, conn);
        }
        // accept succeeded
        sctx.server.incHistoryAcceptedConnectionCount();
        // then, we try to accept again, in case there are pending connections
        accept(ctx);
    }

    @Override
    public void connected(HandlerContext<ServerSocketFD> ctx) {
        // will not fire
        Logger.shouldNotHappen("server should not fire `connected`");
    }

    @Override
    public void readable(HandlerContext<ServerSocketFD> ctx) {
        // will not fire
        Logger.shouldNotHappen("server should not fire readable");
    }

    @Override
    public void writable(HandlerContext<ServerSocketFD> ctx) {
        // will not fire
        Logger.shouldNotHappen("server should not fire writable");
    }

    @Override
    public void removed(HandlerContext<ServerSocketFD> ctx) {
        ServerHandlerContext sctx = (ServerHandlerContext) ctx.getAttachment();
        sctx.server._eventLoop = null;
        sctx.handler.removed(sctx);
    }
}

class NetEventLoopUtils {
    private NetEventLoopUtils() {
    }

    static void callExceptionEvent(ConnectionHandlerContext cctx, IOException err) {
        cctx.handler.exception(cctx, err);
        if (!cctx.connection.isClosed()) {
            cctx.connection.close(true);
            cctx.handler.closed(cctx);
        }
    }

    static void resetCloseTimeout(ConnectionHandlerContext ctx) {
        Connection conn = ctx.connection;
        assert Logger.lowLevelDebug("reset close timeout for connection " + conn);
        conn.lastTimestamp = Config.currentTimestamp;

        if (conn.closeTimeout == null) {
            assert Logger.lowLevelDebug("need to add a new timeout event for " + conn);

            NetEventLoop loop = conn.getEventLoop();
            if (loop == null) {
                if (!conn.isClosed()) {
                    Logger.shouldNotHappen("try to reset close timeout, but the connection is not attached to any event loop: " + conn);
                }
            } else {
                resetDelay(loop, ctx);
            }
        }
    }

    private static void resetDelay(NetEventLoop loop, ConnectionHandlerContext ctx) {
        Connection conn = ctx.connection;
        assert Logger.lowLevelDebug("do reset timeout for " + conn);

        final int timeout = conn.timeout;
        int delay;
        if (conn.lastTimestamp == 0) {
            delay = timeout;
        } else {
            delay = (int) (timeout - (Config.currentTimestamp - conn.lastTimestamp));
        }
        if (delay < 0) {
            Logger.shouldNotHappen("the delay is invalid, timeout = " + timeout + ", current = " + Config.currentTimestamp + ", last = " + conn.lastTimestamp);
            delay = 0;
        }
        assert Logger.lowLevelDebug("the delay for " + conn + " is " + delay);
        conn.closeTimeout = loop.getSelectorEventLoop().delay(delay, () -> {
            // check current timestamp
            int delta = (int) (Config.currentTimestamp - conn.lastTimestamp);
            if (delta > timeout) {
                assert Logger.lowLevelDebug("timeout triggered: " + conn);
                callExceptionEvent(ctx, new SocketTimeoutException("timeout by timer: " + ctx.connection));
            } else {
                resetDelay(loop, ctx);
            }
        });
    }
}

class HandlerForConnection implements Handler<SocketFD> {
    @Override
    public void accept(HandlerContext<SocketFD> ctx) {
        // will not fire
        Logger.shouldNotHappen("connection should not fire accept");
    }

    @Override
    public void connected(HandlerContext<SocketFD> ctx) {
        // will not fire
        Logger.shouldNotHappen("connection should not fire connected");
    }

    @Override
    public void readable(HandlerContext<SocketFD> ctx) {
        ConnectionHandlerContext cctx = (ConnectionHandlerContext) ctx.getAttachment();

        assert Logger.lowLevelDebug("readable fired " + cctx.connection);
        // reset close timer because now it's active (will read some data)
        NetEventLoopUtils.resetCloseTimeout(cctx);

        if (cctx.connection.getInBuffer().free() == 0) {
            Logger.shouldNotHappen("the connection has no space to store data");
            return;
        }
        assert Logger.lowLevelDebug("before calling storeBytesFrom: inBuffer.used() = " + cctx.connection.getInBuffer().used());
        int read;
        try {
            read = cctx.connection.getInBuffer().storeBytesFrom(ctx.getChannel());
        } catch (IOException e) {
            NetEventLoopUtils.callExceptionEvent(cctx, e);
            return;
        }
        assert Logger.lowLevelNetDebug("read " + read + " bytes from " + cctx.connection);
        if (read < 0) {
            // EOF, the remote write is closed
            // the event may fire multiple times (when buffer writable fires, OP_READ will be added)
            // so add the check here, let remoteClosed event fire only once
            if (!cctx.connection.remoteClosed) {
                cctx.connection.remoteClosed = true;
                assert Logger.lowLevelDebug("connection " + cctx.connection + " remote closed");
                cctx.handler.remoteClosed(cctx);
            }
            if (!cctx.connection.isClosed()) {
                ctx.rmOps(EventSet.read()); // do not read anymore, it will always fire OP_READ with EOF
            }
            return;
        }
        if (read == 0) {
            Logger.shouldNotHappen("read nothing, the event should not be fired");
            return;
        }

        cctx.connection.incFromRemoteBytes(read); // record net flow, it's reading, so is "from remote"
        cctx.handler.readable(cctx); // the in buffer definitely have some bytes, let client code read
        if (cctx.connection.getInBuffer().free() == 0) {
            // the in-buffer is full, and client code cannot read, remove read event
            assert Logger.lowLevelDebug("the inBuffer is full now, remove READ event " + cctx.connection);
            if (ctx.getChannel().isOpen()) { // the connection might be closed in readable(), so let's check
                ctx.rmOps(EventSet.read());
            }
        }
    }

    @Override
    public void writable(HandlerContext<SocketFD> ctx) {
        ConnectionHandlerContext cctx = (ConnectionHandlerContext) ctx.getAttachment();
        if (cctx.connection.getOutBuffer().used() == 0) {
            Logger.shouldNotHappen("the connection has nothing to write " + cctx.connection);
            return;
        }

        // reset close timer because now it's active (will send some data)
        NetEventLoopUtils.resetCloseTimeout(cctx);

        boolean writableFires;
        {
            FD fd = ctx.getChannel();
            if (fd instanceof VirtualFD) {
                // virtual fd writable might be removed when handling other fds
                writableFires = ctx.getEventLoop().selector.firingEvents((VirtualFD) fd).have(Event.WRITABLE);
            } else {
                // non-virtual fd should not be affected by other fds
                // and retrieving events of a non-virtual fd might cost too much
                writableFires = true;
            }
        }
        int write;
        try {
            write = cctx.connection.getOutBuffer().writeTo(ctx.getChannel());
        } catch (IOException e) {
            NetEventLoopUtils.callExceptionEvent(cctx, e);
            return;
        }
        assert Logger.lowLevelDebug("wrote " + write + " bytes to " + cctx.connection);
        if (write <= 0) {
            // check whether writable event has already been removed
            // if so, no need to print error
            // otherwise should log
            if (writableFires) {
                Logger.shouldNotHappen("wrote nothing, the event should not be fired: " + cctx.connection);
                // we ignore it for now
            }
            return;
        }
        cctx.connection.incToRemoteBytes(write); // record net flow, it's writing, so is "to remote"
        // NOTE: should also record in Quick Write impl in Connection.java
        cctx.handler.writable(cctx); // the out buffer definitely have some free space, let client code write
        if (cctx.connection.getOutBuffer().used() == 0) {
            // all bytes flushed, and no client bytes for now, remove write event
            assert Logger.lowLevelDebug("the outBuffer is empty now, remove WRITE event " + cctx.connection);
            ctx.rmOps(EventSet.write());
            if (cctx.connection.isWriteClosed()) {
                if (cctx.connection.remoteClosed) {
                    // both directions closed
                    // close the connection
                    cctx.connection.close();
                    cctx.handler.closed(cctx);
                } else {
                    cctx.connection.closeWrite();
                }
            }
        }
    }

    @Override
    public void removed(HandlerContext<SocketFD> ctx) {
        ConnectionHandlerContext cctx = (ConnectionHandlerContext) ctx.getAttachment();
        cctx.connection.releaseEventLoopRelatedFields();
        cctx.handler.removed(cctx);
    }
}

class HandlerForConnectableConnection extends HandlerForConnection {
    @Override
    public void connected(HandlerContext<SocketFD> ctx) {
        // only tcp SocketChannel will fire connected event

        ConnectableConnectionHandlerContext cctx = (ConnectableConnectionHandlerContext) ctx.getAttachment();
        SocketFD channel = ctx.getChannel();
        boolean connected;
        try {
            connected = channel.finishConnect();
        } catch (IOException e) {
            // exception when connecting
            NetEventLoopUtils.callExceptionEvent(cctx, e);
            return;
        }
        cctx.connection.regenId();
        if (!connected) {
            Logger.shouldNotHappen("the connection is not connected, should not fire the event");
        }

        EventSet ops = EventSet.read();
        if (cctx.connection.getOutBuffer().used() > 0) {
            ops = ops.combine(EventSet.write());
        }
        ctx.modify(ops);
        cctx.handler.connected(cctx);
    }
}
