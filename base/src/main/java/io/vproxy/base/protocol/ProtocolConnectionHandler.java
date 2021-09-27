package vproxy.base.protocol;

import vproxy.base.connection.ConnectionHandler;
import vproxy.base.connection.ConnectionHandlerContext;
import vproxy.base.util.Logger;
import vproxy.base.util.Utils;

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
        assert Logger.lowLevelDebug("ProtocolConnectionHandler.exception: " + pctx);
        ProtocolHandler handler = (ProtocolHandler) ctx.attachment;
        if (Utils.isTerminatedIOException(err)) {
            handler.end(pctx);
        } else {
            handler.exception(pctx, err);
        }
        ctx.connection.close(true); // close the connection when got exception
    }

    @Override
    public void remoteClosed(ConnectionHandlerContext ctx) {
        assert Logger.lowLevelDebug("ProtocolConnectionHandler.remoteClosed: " + pctx);
        // the connection is closed
        // we directly close the connection here regardless of data loss
        ctx.connection.close();
        closed(ctx);
    }

    @Override
    public void closed(ConnectionHandlerContext ctx) {
        assert Logger.lowLevelDebug("ProtocolConnectionHandler.closed: " + pctx);
        // do nothing since the `removed` callback will be called just follow the closed event
    }

    @Override
    public void removed(ConnectionHandlerContext ctx) {
        assert Logger.lowLevelDebug("ProtocolConnectionHandler.removed: " + pctx);
        ProtocolHandler handler = (ProtocolHandler) ctx.attachment;
        if (handler.closeOnRemoval(pctx)) {
            // close the connection when loop ends
            ctx.connection.close();
        }
        handler.end(pctx); // let handler know whether or not it's removed
    }
}
