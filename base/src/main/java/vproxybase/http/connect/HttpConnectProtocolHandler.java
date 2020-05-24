package vproxybase.http.connect;

import vproxybase.connection.ConnectorProvider;
import vproxybase.connection.Connector;
import vproxybase.http.HttpContext;
import vproxybase.http.HttpProtocolHandler;
import vproxybase.processor.http1.entity.Request;
import vproxybase.protocol.ProtocolHandler;
import vproxybase.protocol.ProtocolHandlerContext;
import vproxybase.util.*;

import java.io.IOException;

public class HttpConnectProtocolHandler
    implements ProtocolHandler<Tuple<HttpConnectContext, Callback<Connector, IOException>>> {

    private final ConnectorProvider connectorProvider;

    public HttpConnectProtocolHandler(ConnectorProvider connectorProvider) {
        this.connectorProvider = connectorProvider;
    }

    @Override
    public void init(ProtocolHandlerContext<Tuple<HttpConnectContext, Callback<Connector, IOException>>> ctx) {
        assert Logger.lowLevelDebug("http connect init " + ctx.connectionId);
        var outCtx = ctx; // for inner class to capture

        HttpConnectContext hcctx = new HttpConnectContext();
        hcctx.handler = new HttpProtocolHandler(false) {
            @Override
            protected void request(ProtocolHandlerContext<HttpContext> ctx) {
                // fetch data from method and url
                // it should be CONNECT host:port
                Request req = ctx.data.result;
                String url = req.uri;

                boolean isConnect;
                // check method
                {
                    String method = req.method;
                    isConnect = method.equalsIgnoreCase("connect");
                    if (!isConnect && !url.startsWith("http://")) {
                        Logger.warn(LogType.INVALID_EXTERNAL_DATA, "method is wrong! expecting CONNECT or proxying for http, " +
                            "but got " + method + " " + url + ", " +
                            "connection: " + ctx.connectionId);
                        outCtx.data.right.failed(new IOException("invalid method " + method));
                        return;
                    }
                }
                if (isConnect) {
                    String host;
                    int port;
                    // check url for connect
                    {
                        url = ctx.data.result.uri;
                        if (!url.contains(":")) {
                            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "url is wrong! no `:` in " + url + ", " +
                                "connection: " + ctx.connectionId);
                            outCtx.data.right.failed(new IOException("invalid url: " + url));
                            return;
                        }
                        host = url.substring(0, url.lastIndexOf(":")).trim();
                        String strPort = url.substring(url.lastIndexOf(":") + 1).trim();
                        if (host.isEmpty()) {
                            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "url is wrong! invalid host " + url + ", " +
                                "connection: " + ctx.connectionId);
                            outCtx.data.right.failed(new IOException("invalid url: host: " + url));
                            return;
                        }
                        try {
                            port = Integer.parseInt(strPort);
                        } catch (NumberFormatException e) {
                            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "url is wrong! invalid port " + url + ", " +
                                "connection: " + ctx.connectionId);
                            outCtx.data.right.failed(new IOException("invalid url: port: " + url));
                            return;
                        }
                        if (port <= 0 || port > 65535) {
                            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "url is wrong! invalid port: out of range " + url + ", " +
                                "connection: " + ctx.connectionId);
                            outCtx.data.right.failed(new IOException("invalid url: port: out of range: " + url));
                            return;
                        }
                    }

                    assert Logger.lowLevelDebug("connect to " + host + ":" + port);
                    // mark handshake done
                    hcctx.handshakeDone = true;
                    outCtx.data.left.host = host;
                    outCtx.data.left.port = port;
                    outCtx.write("HTTP/1.0 200 Connection established\r\n\r\n".getBytes());
                } else {
                    assert Logger.lowLevelDebug("client is sending raw http request");
                    String msg = "only \"connect\" proxy requests are supported";
                    String rebuiltUrl = "https://" + url.substring("http://".length());
                    String respBody = ErrorPages.build("VPROXY ERROR PAGE", msg, "you may try to request via https", rebuiltUrl, rebuiltUrl);
                    String resp = "" +
                        "HTTP/1.0 400 Bad Request\r\n" +
                        "Connection: Close\r\n" +
                        "Content-Length: " + respBody.getBytes().length + "\r\n" +
                        "\r\n" +
                        respBody;
                    outCtx.write(resp.getBytes());
                    outCtx.data.right.failed(new IOException(msg));
                }
            }
        };
        hcctx.handlerCtx = new ProtocolHandlerContext<>(ctx.connectionId, ctx.connection, ctx.loop, hcctx.handler);
        hcctx.handler.init(hcctx.handlerCtx);
        ctx.data = new Tuple<>(hcctx, null);
    }

    @Override
    public void readable(ProtocolHandlerContext<Tuple<HttpConnectContext, Callback<Connector, IOException>>> ctx) {
        if (ctx.data.left.callbackDone) {
            return;
        }
        if (ctx.data.left.handshakeDone) {
            ctx.data.left.callbackDone = true;
            connectorProvider.provide(ctx.connection,
                ctx.data.left.host, ctx.data.left.port,
                connector -> ctx.data.right.succeeded(connector));
        } else {
            ctx.data.left.handler.readable(ctx.data.left.handlerCtx);
        }
    }

    @Override
    public void exception(ProtocolHandlerContext<Tuple<HttpConnectContext, Callback<Connector, IOException>>> ctx, Throwable err) {
        // connection should be closed by the protocol lib
        // we ignore the exception here
        assert Logger.lowLevelDebug("http connect exception " + ctx.connectionId + ", " + err);
    }

    @Override
    public void end(ProtocolHandlerContext<Tuple<HttpConnectContext, Callback<Connector, IOException>>> ctx) {
        // connection is closed by the protocol lib
        // we ignore the event here
        assert Logger.lowLevelDebug("http connect end " + ctx.connectionId);
    }

    @Override
    public boolean closeOnRemoval(ProtocolHandlerContext<Tuple<HttpConnectContext, Callback<Connector, IOException>>> ctx) {
        if (ctx.data == null || ctx.data.left == null) {
            // return true when it's not fully initialized
            return true;
        }
        // otherwise check whether it's done
        return !ctx.data.left.callbackDone;
    }
}
