package net.cassite.vproxy.http.connect;

import net.cassite.vproxy.http.HttpContext;
import net.cassite.vproxy.http.HttpProtocolHandler;
import net.cassite.vproxy.protocol.ProtocolHandlerContext;

public class HttpConnectContext {
    HttpProtocolHandler handler;
    ProtocolHandlerContext<HttpContext> handlerCtx;
    boolean handshakeDone = false;
    boolean callbackDone = false;

    String host;
    int port;
}
