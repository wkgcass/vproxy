package net.cassite.vproxy.protocol;

import net.cassite.vproxy.connection.ConnectionHandler;
import net.cassite.vproxy.connection.ConnectionHandlerContext;

import java.io.IOException;

@SuppressWarnings("unchecked")
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
