package net.cassite.vproxy.protocol;

import net.cassite.vproxy.connection.*;
import net.cassite.vproxy.selector.SelectorEventLoop;
import net.cassite.vproxy.util.LogType;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.RingBuffer;
import net.cassite.vproxy.util.Tuple;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public class ProtocolEventLoop {
    class ProtocolServerHandler implements ServerHandler {
        private final int inBufferSize;
        private final int outBufferSize;

        public ProtocolServerHandler(int inBufferSize, int outBufferSize) {
            this.inBufferSize = inBufferSize;
            this.outBufferSize = outBufferSize;
        }

        @Override
        public void acceptFail(ServerHandlerContext ctx, IOException err) {
            // we ignore accept fail
            // only do a log
            Logger.error(LogType.SERVER_ACCEPT_FAIL, "server accept new connection failed", err);
        }

        @Override
        public void connection(ServerHandlerContext ctx, Connection connection) {
            ProtocolHandler handler = (ProtocolHandler) ctx.attachment;
            ProtocolHandlerContext pctx = new ProtocolHandlerContext(connection, selectorEventLoop, handler);
            handler.init(pctx);
            try {
                eventLoop.addConnection(connection, handler, new ProtocolConnectionHandler(pctx));
            } catch (IOException e) {
                // handle exception in handler
                handler.exception(pctx, e);
                // and do some log
                Logger.error(LogType.EVENT_LOOP_ADD_FAIL, "add new connection into loop failed", e);
            }
        }

        @Override
        public Tuple<RingBuffer, RingBuffer> getIOBuffers(SocketChannel channel) {
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

    class ProtocolConnectionHandler implements ConnectionHandler {
        private final ProtocolHandlerContext pctx;

        ProtocolConnectionHandler(ProtocolHandlerContext pctx) {
            this.pctx = pctx;
        }

        @Override
        public void readable(ConnectionHandlerContext ctx) {
            ProtocolHandler handler = (ProtocolHandler) ctx.attachment;
            handler.readable(pctx);
        }

        @Override
        public void writable(ConnectionHandlerContext ctx) {
            pctx.doWrite();
        }

        @Override
        public void exception(ConnectionHandlerContext ctx, IOException err) {
            ProtocolHandler handler = (ProtocolHandler) ctx.attachment;
            handler.exception(pctx, err);
            ctx.connection.close(); // close the connection when got exception
        }

        @Override
        public void closed(ConnectionHandlerContext ctx) {
            // do nothing since the `removed` callback will be called just follow the closed event
        }

        @Override
        public void removed(ConnectionHandlerContext ctx) {
            ProtocolHandler handler = (ProtocolHandler) ctx.attachment;
            // close the connection when loop ends
            ctx.connection.close();
            handler.end(pctx);
        }
    }

    private final NetEventLoop eventLoop;
    private final SelectorEventLoop selectorEventLoop;

    // the netEventLoop should be built from selectorEventLoop
    public ProtocolEventLoop(NetEventLoop eventLoop,
                             SelectorEventLoop selectorEventLoop) {
        this.eventLoop = eventLoop;
        this.selectorEventLoop = selectorEventLoop;
    }

    public void register(BindServer server, ProtocolServerConfig config, ProtocolHandler handler) throws IOException {
        eventLoop.addServer(server, handler, new ProtocolServerHandler(config.inBufferSize, config.outBufferSize));
    }
}
