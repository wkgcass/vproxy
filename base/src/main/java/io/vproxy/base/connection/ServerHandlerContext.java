package io.vproxy.base.connection;

public class ServerHandlerContext {
    public final NetEventLoop eventLoop;
    public final ServerSock server;
    public final Object attachment;
    final ServerHandler handler;

    ServerHandlerContext(NetEventLoop eventLoop, ServerSock server, Object attachment, ServerHandler handler) {
        this.eventLoop = eventLoop;
        this.server = server;
        this.attachment = attachment;
        this.handler = handler;
    }
}
