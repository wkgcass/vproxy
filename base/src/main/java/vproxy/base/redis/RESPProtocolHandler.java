package vproxy.base.redis;

import vproxy.base.protocol.ProtocolHandler;
import vproxy.base.protocol.ProtocolHandlerContext;
import vproxy.base.util.Callback;
import vproxy.base.util.LogType;
import vproxy.base.util.Logger;
import vproxy.base.util.Utils;

public class RESPProtocolHandler implements ProtocolHandler<RESPContext> {
    private final RESPConfig config;
    private final RESPHandler handler;

    public RESPProtocolHandler(RESPConfig config, RESPHandler handler) {
        this.config = config;
        this.handler = handler;
    }

    @Override
    public void init(ProtocolHandlerContext<RESPContext> ctx) {
        assert Logger.lowLevelDebug("connection established in RESPProtocolHandler " + ctx.connectionId);
        // init resp context
        ctx.data = new RESPContext();
        ctx.data.attachment = handler.attachment();
    }

    @Override
    public void readable(ProtocolHandlerContext<RESPContext> ctx) {
        if (ctx.data.parser == null) {
            ctx.data.parser = new RESPParser(config.maxParseLen);
        }
        int r = ctx.data.parser.feed(ctx.inBuffer);
        if (r == -1) {
            String error = ctx.data.parser.getErrorMessage();
            if (error == null)
                return; // no error and -1 means want more data
            ctx.inBuffer.clear(); // remove pending input data
            ctx.data.parser = null; // remove the parser
            ctx.write(Serializer.fromErrorString(error));
            return;
        }
        Object o = ctx.data.parser.getResult().getJavaObject();
        ctx.data.parser = null; // remove the parser
        // let user code handle the object
        try {
            //noinspection unchecked
            handler.handle(o, ctx.data.attachment, new Callback<Object, Throwable>() {
                @Override
                protected void onSucceeded(Object value) {
                    byte[] bytes;
                    try {
                        bytes = Serializer.from(value);
                    } catch (IllegalArgumentException e) {
                        Logger.error(LogType.IMPROPER_USE, "user returned an unexpected object", e);
                        ctx.write(Serializer.fromErrorString("Internal Error"));
                        return;
                    }
                    ctx.write(bytes);
                }

                @Override
                protected void onFailed(Throwable err) {
                    Logger.info(LogType.USER_HANDLE_FAIL, "user handling failed in RESPProtocolHandler in conn: " + ctx.connectionId +
                        " - " + err.getClass().getSimpleName() +
                        " - " + Utils.formatErr(err));
                    assert Logger.printStackTrace(err);
                    String errStr = Utils.formatErr(err);
                    ctx.write(Serializer.fromErrorString(errStr));
                }
            });
        } catch (Throwable t) {
            Logger.error(LogType.IMPROPER_USE, "user handle function thrown error", t);
            ctx.write(Serializer.fromErrorString("Internal Error"));
        }
    }

    @Override
    public void exception(ProtocolHandlerContext<RESPContext> ctx, Throwable err) {
        Logger.error(LogType.CONN_ERROR, "exception in RESPProtocolHandler in conn: " + ctx.connectionId, err);
    }

    @Override
    public void end(ProtocolHandlerContext<RESPContext> ctx) {
        // ignore because it's a request/response server
        assert Logger.lowLevelDebug("connection end in RESPProtocolHandler " + ctx.connectionId);
    }
}
