package vproxy.http.connect;

import vproxy.http.HttpContext;
import vproxy.http.HttpProtocolHandler;
import vproxy.protocol.ProtocolHandlerContext;

public class HttpConnectContext {
    HttpProtocolHandler handler;
    ProtocolHandlerContext<HttpContext> handlerCtx;
    boolean handshakeDone = false;
    boolean callbackDone = false;

    String host;
    int port;
}
