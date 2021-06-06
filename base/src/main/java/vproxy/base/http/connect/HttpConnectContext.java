package vproxy.base.http.connect;

import vproxy.base.http.HttpContext;
import vproxy.base.http.HttpProtocolHandler;
import vproxy.base.protocol.ProtocolHandlerContext;

public class HttpConnectContext {
    HttpProtocolHandler handler;
    ProtocolHandlerContext<HttpContext> handlerCtx;
    boolean handshakeDone = false;
    boolean callbackDone = false;

    String host;
    int port;
}
