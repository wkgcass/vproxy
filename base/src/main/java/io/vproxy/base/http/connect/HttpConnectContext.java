package io.vproxy.base.http.connect;

import io.vproxy.base.http.HttpContext;
import io.vproxy.base.http.HttpProtocolHandler;
import io.vproxy.base.protocol.ProtocolHandlerContext;

public class HttpConnectContext {
    HttpProtocolHandler handler;
    ProtocolHandlerContext<HttpContext> handlerCtx;
    boolean handshakeDone = false;
    boolean callbackDone = false;

    String host;
    int port;
}
