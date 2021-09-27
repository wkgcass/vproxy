package io.vproxy.base.http.connect;

import io.vproxy.base.connection.Connector;
import io.vproxy.base.connection.ConnectorProvider;
import io.vproxy.base.http.HttpContext;
import io.vproxy.base.http.HttpProtocolHandler;
import io.vproxy.base.processor.http1.entity.Request;
import io.vproxy.base.protocol.ProtocolHandler;
import io.vproxy.base.protocol.ProtocolHandlerContext;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.callback.Callback;
import io.vproxy.base.util.coll.Tuple;
import io.vproxy.base.util.nio.ByteArrayChannel;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;

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
                // it should be CONNECT host:port or METHOD http://host[:port] VERSION
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
                    assert Logger.lowLevelDebug("client is sending raw http request: " + req);
                    String target = req.uri;
                    assert target.startsWith("http://");
                    target = target.substring("http://".length());
                    String formattedUri;
                    if (target.contains("/")) {
                        formattedUri = target.substring(target.indexOf("/"));
                        target = target.substring(0, target.indexOf("/"));
                    } else {
                        formattedUri = "/";
                    }
                    assert Logger.lowLevelDebug("proxy target string is " + target + ", the formatted uri is " + formattedUri);
                    // maybe ip:port, domain:port, ip[:80], domain[:80]
                    if (IPPort.validL4AddrStr(target)) {
                        IPPort targetL4Addr = new IPPort(target);
                        assert Logger.lowLevelDebug("proxy target is " + targetL4Addr);
                        directHttpProxy(outCtx, hcctx, ctx, targetL4Addr, formattedUri);
                    } else if (IP.isIpLiteral(target)) {
                        IPPort targetL4Addr = new IPPort(IP.from(target), 80);
                        assert Logger.lowLevelDebug("proxy target is " + targetL4Addr);
                        directHttpProxy(outCtx, hcctx, ctx, targetL4Addr, formattedUri);
                    } else if (target.contains(":")) {
                        String portStr = target.substring(target.lastIndexOf(":") + 1);
                        String domain = target.substring(0, target.lastIndexOf(":"));
                        int port;
                        try {
                            port = Integer.parseInt(portStr);
                        } catch (NumberFormatException e) {
                            Logger.error(LogType.INVALID_EXTERNAL_DATA, "port number is not valid: " + portStr);
                            outCtx.data.right.failed(new IOException("invalid port number " + portStr));
                            return;
                        }
                        assert Logger.lowLevelDebug("proxy target is " + domain + ":" + port);
                        directHttpProxy(outCtx, hcctx, ctx, domain, port, formattedUri);
                    } else {
                        //noinspection UnnecessaryLocalVariable
                        String domain = target;
                        int port = 80;
                        assert Logger.lowLevelDebug("proxy target is " + domain + ":" + port);
                        directHttpProxy(outCtx, hcctx, ctx, domain, port, formattedUri);
                    }
                }
            }
        };
        hcctx.handlerCtx = new ProtocolHandlerContext<>(ctx.connectionId, ctx.connection, ctx.loop, hcctx.handler);
        hcctx.handler.init(hcctx.handlerCtx);
        ctx.data = new Tuple<>(hcctx, null);
    }

    private boolean failedStoringFormattedRequestBackToInBuffer(ProtocolHandlerContext<Tuple<HttpConnectContext, Callback<Connector, IOException>>> outCtx,
                                                                ProtocolHandlerContext<HttpContext> ctx,
                                                                String formattedUri) {
        ctx.data.result.uri = formattedUri;
        ByteArray reqBytes = ctx.data.result.toByteArray();
        ByteArrayChannel chnl = ByteArrayChannel.fromFull(reqBytes);
        int stored = ctx.connection.getInBuffer().storeBytesFrom(chnl); // is expected to be fully stored
        if (stored != reqBytes.length()) {
            Logger.error(LogType.ALERT, "the formatted request (" + reqBytes.length() + " bytes) is not fully stored back into the inBuffer");
            outCtx.data.right.failed(new IOException("request too long"));
            return true;
        }
        assert Logger.lowLevelDebug("the request is fully stored back into the inBuffer:\n" + new String(reqBytes.toJavaArray()));
        return false;
    }

    private void directHttpProxy(ProtocolHandlerContext<Tuple<HttpConnectContext, Callback<Connector, IOException>>> outCtx,
                                 HttpConnectContext hcctx,
                                 ProtocolHandlerContext<HttpContext> ctx,
                                 IPPort remote, String formattedUri) {
        if (failedStoringFormattedRequestBackToInBuffer(outCtx, ctx, formattedUri)) {
            return; // error would already be logged and callback would already be called
        }
        // mark handshake done
        hcctx.handshakeDone = true;
        outCtx.data.left.host = remote.getAddress().formatToIPString();
        outCtx.data.left.port = remote.getPort();
        readable(outCtx);
    }

    private void directHttpProxy(ProtocolHandlerContext<Tuple<HttpConnectContext, Callback<Connector, IOException>>> outCtx,
                                 HttpConnectContext hcctx,
                                 ProtocolHandlerContext<HttpContext> ctx,
                                 String domain,
                                 int port,
                                 String formattedUri) {
        if (failedStoringFormattedRequestBackToInBuffer(outCtx, ctx, formattedUri)) {
            return; // error would already be logged and callback would already be called
        }
        // mark handshake done
        hcctx.handshakeDone = true;
        outCtx.data.left.host = domain;
        outCtx.data.left.port = port;
        readable(outCtx);
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
