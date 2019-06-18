package vproxy.protocol;

import vproxy.connection.ConnectionHandler;
import vproxy.connection.ConnectionHandlerContext;

import java.io.IOException;

@SuppressWarnings("unchecked")
public class ProtocolConnectionHandler implements ConnectionHandler {
    private final ProtocolHandlerContext pctx;

    public ProtocolConnectionHandler(ProtocolHandlerContext pctx) {
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
        if ("Connection reset by peer".equals(err.getMessage())) {
            handler.end(pctx);
        } else {
            handler.exception(pctx, err);
        }
        ctx.connection.close(); // close the connection when got exception
    }

    @Override
    public void closed(ConnectionHandlerContext ctx) {
        // do nothing since the `removed` callback will be called just follow the closed event
    }

    @Override
    public void removed(ConnectionHandlerContext ctx) {
        ProtocolHandler handler = (ProtocolHandler) ctx.attachment;
        if (handler.closeOnRemoval(pctx)) {
            // close the connection when loop ends
            ctx.connection.close();
        }
        handler.end(pctx); // let handler know whether or not it's removed
    }
}
