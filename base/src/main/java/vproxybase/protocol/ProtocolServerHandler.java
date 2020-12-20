package vproxybase.protocol;

import vfd.SocketFD;
import vproxybase.connection.*;
import vproxybase.util.LogType;
import vproxybase.util.Logger;
import vproxybase.util.RingBuffer;
import vproxybase.util.Tuple;

import java.io.IOException;

public class ProtocolServerHandler implements ServerHandler {
    private NetEventLoop eventLoop;
    private final int inBufferSize;
    private final int outBufferSize;

    private ProtocolServerHandler(NetEventLoop eventLoop, int inBufferSize, int outBufferSize) {
        this.eventLoop = eventLoop;
        this.inBufferSize = inBufferSize;
        this.outBufferSize = outBufferSize;
    }

    public static void apply(NetEventLoop eventLoop,
                             ServerSock server, ProtocolServerConfig config,
                             ProtocolHandler handler) throws IOException {
        eventLoop.addServer(server, handler, new ProtocolServerHandler(eventLoop, config.inBufferSize, config.outBufferSize));
    }

    @Override
    public void acceptFail(ServerHandlerContext ctx, IOException err) {
        // we ignore accept fail
        // only do a log
        Logger.error(LogType.SERVER_ACCEPT_FAIL, "server accept new connection failed", err);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void connection(ServerHandlerContext ctx, Connection connection) {
        ProtocolHandler handler = (ProtocolHandler) ctx.attachment;
        ProtocolHandlerContext pctx = new ProtocolHandlerContext(connection.id(), connection, eventLoop, handler);
        handler.init(pctx);
        // Proxy.java copies these codes:
        //noinspection Duplicates
        try {
            eventLoop.addConnection(connection, handler, new ProtocolConnectionHandler(pctx));
        } catch (IOException e) {
            // handle exception in handler
            handler.exception(pctx, e);
            // and do some log
            Logger.error(LogType.EVENT_LOOP_ADD_FAIL, "add new connection into loop failed", e);
            // the connection should be closed by the lib
            connection.close();
        }
    }

    @Override
    public Tuple<RingBuffer, RingBuffer> getIOBuffers(SocketFD channel) {
        return new Tuple<>(
            RingBuffer.allocate(inBufferSize),
            RingBuffer.allocate(outBufferSize)
        );
    }

    @Override
    public void removed(ServerHandlerContext ctx) {
        // close the server when removed from eventLoop
        ctx.server.close();
    }
}
