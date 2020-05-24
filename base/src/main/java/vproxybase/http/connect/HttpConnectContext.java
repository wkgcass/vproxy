package vproxybase.http.connect;

import vproxybase.http.HttpContext;
import vproxybase.http.HttpProtocolHandler;
import vproxybase.protocol.ProtocolHandlerContext;

public class HttpConnectContext {
    HttpProtocolHandler handler;
    ProtocolHandlerContext<HttpContext> handlerCtx;
    boolean handshakeDone = false;
    boolean callbackDone = false;

    String host;
    int port;
}
