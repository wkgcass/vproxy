package vserver.impl;

import vlibbase.Conn;
import vlibbase.impl.ConnImpl;
import vfd.SocketFD;
import vproxybase.connection.*;
import vproxybase.util.LogType;
import vproxybase.util.Logger;
import vproxybase.util.RingBuffer;
import vproxybase.util.Tuple;
import vserver.StreamServer;

import java.io.IOException;

public class StreamServerImpl extends AbstractServer implements StreamServer {
    private AcceptHandler acceptHandler;

    public StreamServerImpl() {
    }

    public StreamServerImpl(NetEventLoop loop) {
        super(loop);
    }

    @Override
    public StreamServer accept(AcceptHandler handler) {
        if (acceptHandler != null) {
            throw new IllegalStateException("accept handler is already set");
        }
        this.acceptHandler = handler;
        return this;
    }

    @Override
    public void listen(ServerSock server) throws IOException {
        if (acceptHandler == null) {
            throw new IllegalStateException("accept handler is not set but listen(ServerSock) is called");
        }
        if (loop == null) {
            throw new IllegalStateException("loop not specified but listen(ServerSock) is called");
        }
        loop.addServer(server, null, new ServerHandler() {
            @Override
            public void acceptFail(ServerHandlerContext ctx, IOException err) {
                Logger.error(LogType.SERVER_ACCEPT_FAIL, "accepting connection for " + ctx.server + " failed", err);
            }

            @Override
            public void connection(ServerHandlerContext ctx, Connection connection) {
                Conn conn;
                try {
                    conn = new ConnImpl(loop, connection, null);
                } catch (IOException ignore) {
                    connection.close();
                    return; // should already been logged in the ConnImpl constructor
                }
                acceptHandler.handle(conn);
            }

            @Override
            public Tuple<RingBuffer, RingBuffer> getIOBuffers(SocketFD channel) {
                return new Tuple<>(
                    RingBuffer.allocate(24576),
                    RingBuffer.allocate(24576)
                );
            }

            @Override
            public void removed(ServerHandlerContext ctx) {
                Logger.error(LogType.IMPROPER_USE, "the server is removed from loop " + ctx.server + ", should close it");
                close();
            }
        });
    }
}
