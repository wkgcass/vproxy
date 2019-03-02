package net.cassite.vproxy.http;

import net.cassite.vproxy.protocol.ProtocolHandler;
import net.cassite.vproxy.protocol.ProtocolHandlerContext;
import net.cassite.vproxy.util.ByteArrayChannel;
import net.cassite.vproxy.util.Logger;

public abstract class HttpProtocolHandler implements ProtocolHandler<HttpContext> {
    private final boolean parseBody;

    protected HttpProtocolHandler(boolean parseBody) {
        this.parseBody = parseBody;
    }

    @Override
    public void init(ProtocolHandlerContext<HttpContext> ctx) {
        ctx.data = new HttpContext();
    }

    @Override
    public void readable(ProtocolHandlerContext<HttpContext> ctx) {
        if (ctx.data.parser == null) {
            ctx.data.parser = new HttpReqParser(parseBody);
        }
        int err = ctx.data.parser.feed(ctx.inBuffer);
        if (err == 0) {
            // parse done
            ctx.data.result = ctx.data.parser.getResult();
            ctx.data.parser = null;
            request(ctx);
        } else {
            String errMsg = ctx.data.parser.getErrorMessage();
            if (errMsg != null) {
                sendError(ctx, errMsg);
            } // otherwise means want more data
        }
    }

    protected void sendError(ProtocolHandlerContext<HttpContext> ctx, String errMsg) {
        // flush all input data
        if (ctx.inBuffer.used() > 0) {
            byte[] foo = new byte[ctx.inBuffer.used()];
            ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(foo);
            while (ctx.inBuffer.used() != 0) {
                chnl.reset();
                ctx.inBuffer.writeTo(chnl);
            }
        }

        // send back error response
        String htmlErrMsg = "<html><body><h1>" + errMsg + "</h1></body></html>\r\n";
        String sendBack = "" +
            "HTTP/1.1 400 Bad Request\r\n" +
            "Connection: Keep-Alive\r\n" + // we want to keep the connection open anyway
            "Content-Length: " + htmlErrMsg.length() + "\r\n" +
            "\r\n" +
            htmlErrMsg;
        ctx.write(sendBack.getBytes());
    }

    protected abstract void request(ProtocolHandlerContext<HttpContext> ctx);

    @Override
    public void exception(ProtocolHandlerContext<HttpContext> ctx, Throwable err) {
        // connection should be closed by the protocol lib
        // we ignore the exception here
        assert Logger.lowLevelDebug("http exception " + ctx.connectionId + ", " + err);
    }

    @Override
    public void end(ProtocolHandlerContext<HttpContext> ctx) {
        // connection is closed by the protocol lib
        // we ignore the event here
        assert Logger.lowLevelDebug("http end " + ctx.connectionId);
    }

    @Override
    public boolean closeOnRemoval(ProtocolHandlerContext<HttpContext> ctx) {
        // true if data is not initialized or parse not done
        return ctx.data == null || ctx.data.parser != null;
    }
}
