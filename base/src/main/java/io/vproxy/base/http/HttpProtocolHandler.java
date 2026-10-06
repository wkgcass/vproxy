package io.vproxy.base.http;

import io.vproxy.base.protocol.ProtocolHandler;
import io.vproxy.base.protocol.ProtocolHandlerContext;
import io.vproxy.base.util.Logger;

import java.nio.charset.StandardCharsets;

public abstract class HttpProtocolHandler implements ProtocolHandler<HttpContext> {
    private final HttpReqParser.Params params;

    protected HttpProtocolHandler(HttpReqParser.Params params) {
        this.params = new HttpReqParser.Params(params);
    }

    @Override
    public void init(ProtocolHandlerContext<HttpContext> ctx) {
        ctx.data = new HttpContext();
    }

    @Override
    public void readable(ProtocolHandlerContext<HttpContext> ctx) {
        if (ctx.data.terminated) return;
        if (ctx.data.parser == null) {
            ctx.data.parser = new HttpReqParser(params);
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
        int status = ctx.data.parser == null ? 400 : ctx.data.parser.getErrorStatusCode();
        ctx.data.terminated = true;
        ctx.data.parser = null;
        ctx.data.result = null;
        ctx.inBuffer.clear();

        // send back error response
        String htmlErrMsg = "<html><body><h1>" + errMsg.replace("&", "&amp;")
            .replace("<", "&lt;").replace(">", "&gt;") + "</h1></body></html>\r\n";
        String reason = switch (status) {
            case 414 -> "URI Too Long";
            case 431 -> "Request Header Fields Too Large";
            default -> "Bad Request";
        };
        String sendBack = "" +
            "HTTP/1.1 " + status + " " + reason + "\r\n" +
            "Connection: close\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: " + htmlErrMsg.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
            "\r\n" +
            htmlErrMsg;
        ctx.write(sendBack.getBytes(StandardCharsets.UTF_8));
        ctx.closeAfterFlush();
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
        return ctx.data == null || ctx.data.terminated || ctx.data.parser != null;
    }
}
