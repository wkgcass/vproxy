package net.cassite.vproxy.protocol;

import net.cassite.vproxy.connection.*;
import net.cassite.vproxy.util.LogType;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.RingBuffer;
import net.cassite.vproxy.util.Tuple;

import java.io.IOException;
import java.nio.channels.NetworkChannel;

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
                             BindServer server, ProtocolServerConfig config,
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
        ProtocolHandlerContext pctx = new ProtocolHandlerContext(connection.id(), connection, eventLoop.getSelectorEventLoop(), handler);
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
    public Tuple<RingBuffer, RingBuffer> getIOBuffers(NetworkChannel channel) {
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
