package net.cassite.vproxy.connection;

import net.cassite.vproxy.selector.Handler;
import net.cassite.vproxy.selector.HandlerContext;
import net.cassite.vproxy.selector.SelectorEventLoop;
import net.cassite.vproxy.util.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;

public class NetEventLoop {
    private static final HandlerForTCPServer handlerForTPCServer = new HandlerForTCPServer();
    private /*static*/ final HandlerForUDPServer handlerForUDPServer = new HandlerForUDPServer(this);
    private static final HandlerForConnection handlerForConnection = new HandlerForConnection();
    private static final HandlerForClientConnection handlerForClientConnection = new HandlerForClientConnection();

    private final SelectorEventLoop selectorEventLoop;

    public NetEventLoop(SelectorEventLoop selectorEventLoop) {
        this.selectorEventLoop = selectorEventLoop;
    }

    public SelectorEventLoop getSelectorEventLoop() {
        return selectorEventLoop;
    }

    @ThreadSafe
    public void addServer(BindServer server, Object attachment, ServerHandler handler) throws IOException {
        // synchronize in case the fields being inconsistent
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (server) {
            if (server.isClosed())
                throw new ClosedChannelException();
            if (server._eventLoop != null)
                throw new IOException("bindServer already registered to a event loop");
            server._eventLoop = this;
            if (server.protocol == Protocol.TCP) {
                //noinspection unchecked
                selectorEventLoop.add(server.channel, SelectionKey.OP_ACCEPT,
                    new ServerHandlerContext(this, server, attachment, handler),
                    (Handler) handlerForTPCServer);
            } else {
                //noinspection unchecked
                selectorEventLoop.add(server.channel, SelectionKey.OP_READ,
                    new ServerHandlerContext(this, server, attachment, handler),
                    (Handler) handlerForUDPServer);
            }
        }
    }

    @ThreadSafe
    public void removeServer(BindServer server) {
        // event loop in server object will be set to null in remove event
        selectorEventLoop.remove(server.channel);
    }

    private void doAddConnection(Connection connection, int ops, ConnectionHandlerContext att, Handler<SelectableChannel> handler) throws IOException {
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
    }

    @ThreadSafe
    public void addConnection(Connection connection, Object attachment, ConnectionHandler handler) throws IOException {
        int ops = 0;
        if (connection.inBuffer.free() > 0) {
            ops |= SelectionKey.OP_READ;
        }
        if (connection.outBuffer.used() > 0) {
            ops |= SelectionKey.OP_WRITE;
        }

        doAddConnection(connection, ops,
            new ConnectionHandlerContext(this, connection, attachment, handler),
            handlerForConnection);
    }

    // this method is for both server connection and client connection
    @ThreadSafe
    public void removeConnection(Connection connection) {
        // event loop in connection object will be set to null in remove event
        selectorEventLoop.remove(connection.channel);
    }

    @ThreadSafe
    public void addClientConnection(ClientConnection connection, Object attachment, ClientConnectionHandler handler) throws IOException {
        boolean fireConnected = false; // whether to fire `connected` event after registering
        // the connection might already be connected
        // so fire the event to let handler know
        // also it might be UDP

        int ops;
        if (connection.protocol != Protocol.TCP || ((SocketChannel) connection.channel).isConnected()) {
            fireConnected = connection.protocol == Protocol.TCP; // only TCP can have connection and fire event

            ops = 0;
            if (connection.inBuffer.free() > 0)
                ops |= SelectionKey.OP_READ;
            if (connection.outBuffer.used() > 0)
                ops |= SelectionKey.OP_WRITE;
        } else {
            ops = SelectionKey.OP_CONNECT;
        }

        ClientConnectionHandlerContext ctx = new ClientConnectionHandlerContext(this, connection, attachment, handler);

        doAddConnection(connection, ops, ctx, handlerForClientConnection);

        if (fireConnected) {
            try {
                handler.connected(ctx);
            } catch (Throwable t) {
                Logger.error(LogType.IMPROPER_USE, "the connected callback got exception", t);
            }
        }
    }
}

class HandlerForTCPServer implements Handler<ServerSocketChannel> {
    @Override
    public void accept(HandlerContext<ServerSocketChannel> ctx) {
        ServerHandlerContext sctx = (ServerHandlerContext) ctx.getAttachment();

        ServerSocketChannel server = ctx.getChannel();
        SocketChannel sock;
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
                conn = new Connection(Protocol.TCP, sock, (InetSocketAddress) sock.getRemoteAddress(),
                    ioBuffers.left, ioBuffers.right, true/*it IS a connection*/);
            } catch (IOException e) {
                Logger.shouldNotHappen("Connection object create failed: " + e);
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
    public void connected(HandlerContext<ServerSocketChannel> ctx) {
        // will not fire
        Logger.shouldNotHappen("server should not fire `connected`");
    }

    @Override
    public void readable(HandlerContext<ServerSocketChannel> ctx) {
        // will not fire
        Logger.shouldNotHappen("server should not fire readable");
    }

    @Override
    public void writable(HandlerContext<ServerSocketChannel> ctx) {
        // will not fire
        Logger.shouldNotHappen("server should not fire writable");
    }

    @Override
    public void removed(HandlerContext<ServerSocketChannel> ctx) {
        // same as udp removed()
        ServerHandlerContext sctx = (ServerHandlerContext) ctx.getAttachment();
        sctx.server._eventLoop = null;
        sctx.handler.removed(sctx);
    }
}

class HandlerForUDPServer implements Handler<DatagramChannel> {
    // allocate a big buffer for udp temp data
    // the ip packet maximum is 65535 bytes
    // create a buffer of 64k is ok because it's O(1) for any operation within 1 NetEventLoop
    // reuse this buffer for every packet
    // so remember to reset cursors before operating
    private final ByteBuffer buffer = ByteBuffer.allocateDirect(65536);
    private final NetEventLoop netEventLoop;

    HandlerForUDPServer(NetEventLoop netEventLoop) {
        this.netEventLoop = netEventLoop;
    }

    @Override
    public void accept(HandlerContext<DatagramChannel> ctx) {
        // will not fire
    }

    @Override
    public void connected(HandlerContext<DatagramChannel> ctx) {
        // will not fire
    }

    @Override
    public void readable(HandlerContext<DatagramChannel> ctx) {
        ServerHandlerContext sctx = (ServerHandlerContext) ctx.getAttachment();

        // reset cursor of buffer
        buffer.position(0).limit(65536);

        InetSocketAddress remote;
        try {
            remote = (InetSocketAddress) ctx.getChannel().receive(buffer);
        } catch (IOException e) {
            // exception occurred when reading the udp channel
            // maybe it's reset from localhost
            // or maybe a bug
            Logger.shouldNotHappen("reading udp " + ctx.getChannel() + " raise error", e);
            sctx.handler.exception(sctx, e);
            return;
        }

        // read completed, flip the buffer for writing
        buffer.flip();

        if (remote == null) {
            assert Logger.lowLevelDebug("no data yet, ignore this event");
            return;
        }
        BindServer server = sctx.server;
        BindServer.UDPConn udpConn = server.udpDummyConnMap.get(remote);
        if (udpConn == null) {
            // initialize the dummy connection

            Tuple<RingBuffer, RingBuffer> ioBuffers = sctx.handler.getIOBuffers(ctx.getChannel());
            Connection conn;
            try {
                conn = new Connection(Protocol.UDP, ctx.getChannel(), remote,
                    ioBuffers.left, ioBuffers.right, false/*it's nothing like a connection*/);
            } catch (IOException e) {
                Logger.shouldNotHappen("exception occurred when creating connection object for udp channel", e);
                sctx.handler.exception(sctx, e);
                return;
            }

            // retrieve context from user code
            ConnectionHandlerContext cctx = new ConnectionHandlerContext(netEventLoop,
                conn, sctx.attachment,
                sctx.handler.udpHandler(sctx, conn));
            conn.setEventLoopRelatedFields(netEventLoop, cctx);
            conn.addNetFlowRecorder(server); // record net flow
            // build a udp conn and store
            udpConn = server.new UDPConn(remote, conn, cctx);
            conn._udpDummyConn = udpConn;
            server.udpDummyConnMap.put(remote, udpConn);
            // fire connection event
            sctx.handler.connection(sctx, conn);
        }
        int toStore = buffer.remaining();
        int stored = udpConn.connection.inBuffer.storeBytesFrom(buffer);
        // we do not care about the res, but let's log if not reading all
        assert toStore == stored || Logger.lowLevelDebug("toStore = " + toStore + ", stored = " + stored);
        if (stored == 0) {
            // remove the OP_READ if cannot read anymore
            ctx.rmOps(SelectionKey.OP_READ);
            return;
        }
        udpConn.connection.incFromRemoteBytes(stored); // reading from remote channel

        // fire readable event
        udpConn.cctx.handler.readable(udpConn.cctx);
        // writable event will be handled in connection `Quick Write` mechanism

        readable(ctx); // then we call readable again in case now got another packet
    }

    @Override
    public void writable(HandlerContext<DatagramChannel> ctx) {
        // we never add this event
        // so won't fire
    }

    @Override
    public void removed(HandlerContext<DatagramChannel> ctx) {
        // same as tcp removed()
        ServerHandlerContext sctx = (ServerHandlerContext) ctx.getAttachment();
        sctx.server._eventLoop = null;
        sctx.handler.removed(sctx);
    }
}

class HandlerForConnection implements Handler<SelectableChannel> {
    @Override
    public void accept(HandlerContext<SelectableChannel> ctx) {
        // will not fire
        Logger.shouldNotHappen("connection should not fire accept");
    }

    @Override
    public void connected(HandlerContext<SelectableChannel> ctx) {
        // will not fire
        Logger.shouldNotHappen("connection should not fire connected");
    }

    @Override
    public void readable(HandlerContext<SelectableChannel> ctx) {
        ConnectionHandlerContext cctx = (ConnectionHandlerContext) ctx.getAttachment();
        if (cctx.connection.inBuffer.free() == 0) {
            Logger.shouldNotHappen("the connection has no space to store data");
            return;
        }
        int read;
        try {
            read = cctx.connection.inBuffer.storeBytesFrom((ReadableByteChannel) /* it's definitely readable */ ctx.getChannel());
        } catch (IOException e) {
            cctx.handler.exception(cctx, e);
            return;
        }
        if (read < 0) {
            // EOF, the remote write is closed
            cctx.connection.remoteClosed = true;
            assert Logger.lowLevelDebug("connection " + cctx.connection + " remote closed");
            // remove read event add write event (maybe more bytes to write)
            ctx.modify(SelectionKey.OP_WRITE);
            // the connection will be closed after write
            return;
        }
        if (read == 0) {
            Logger.shouldNotHappen("read nothing, the event should not be fired");
            return;
        }
        cctx.connection.incFromRemoteBytes(read); // record net flow, it's reading, so is "from remote"
        cctx.handler.readable(cctx); // the in buffer definitely have some bytes, let client code read
        if (cctx.connection.inBuffer.free() == 0) {
            // the in-buffer is full, and client code cannot read, remove read event
            assert Logger.lowLevelDebug("the inBuffer is full now, remove READ event " + cctx.connection);
            ctx.rmOps(SelectionKey.OP_READ);
        }
    }

    @Override
    public void writable(HandlerContext<SelectableChannel> ctx) {
        ConnectionHandlerContext cctx = (ConnectionHandlerContext) ctx.getAttachment();
        if (cctx.connection.outBuffer.used() == 0) {
            if (cctx.connection.remoteClosed) {
                // no bytes to write, then the connection can be closed now
                cctx.connection.close();
                cctx.handler.closed(cctx);
            } else {
                Logger.shouldNotHappen("the connection has nothing to write " + cctx.connection);
            }
            return;
        }
        int write;
        try {
            write = cctx.connection.outBuffer.writeTo((WritableByteChannel) /* it's definitely writable */ ctx.getChannel());
        } catch (IOException e) {
            cctx.handler.exception(cctx, e);
            return;
        }
        if (write <= 0) {
            Logger.shouldNotHappen("wrote nothing, the event should not be fired");
            // we ignore it for now
            return;
        }
        cctx.connection.incToRemoteBytes(write); // record net flow, it's writing, so is "to remote"
        // NOTE: should also record in Quick Write impl in Connection.java
        cctx.handler.writable(cctx); // the out buffer definitely have some free space, let client code write
        if (cctx.connection.outBuffer.used() == 0) {
            // all bytes flushed, and no client bytes for now, remove write event
            assert Logger.lowLevelDebug("the outBuffer is empty now, remove WRITE event " + cctx.connection);
            ctx.rmOps(SelectionKey.OP_WRITE);
        }
    }

    @Override
    public void removed(HandlerContext<SelectableChannel> ctx) {
        ConnectionHandlerContext cctx = (ConnectionHandlerContext) ctx.getAttachment();
        cctx.connection.releaseEventLoopRelatedFields();
        cctx.handler.removed(cctx);
    }
}

class HandlerForClientConnection extends HandlerForConnection {
    @Override
    public void connected(HandlerContext<SelectableChannel> ctx) {
        // only tcp SocketChannel will fire connected event

        ClientConnectionHandlerContext cctx = (ClientConnectionHandlerContext) ctx.getAttachment();
        SocketChannel channel = (SocketChannel) ctx.getChannel();
        boolean connected;
        try {
            connected = channel.finishConnect();
        } catch (IOException e) {
            // exception when connecting
            cctx.handler.exception(cctx, e);
            return;
        }
        if (!connected) {
            Logger.shouldNotHappen("the connection is not connected, should not fire the event");
        }

        int ops = SelectionKey.OP_READ;
        if (cctx.connection.outBuffer.used() > 0) {
            ops |= SelectionKey.OP_WRITE;
        }
        ctx.modify(ops);
        cctx.handler.connected(cctx);
    }
}
