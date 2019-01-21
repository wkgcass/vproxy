package net.cassite.vproxy.connection;

public class ServerHandlerContext {
    public final NetEventLoop eventLoop;
    public final BindServer server;
    public final Object attachment;
    final ServerHandler handler;

    ServerHandlerContext(NetEventLoop eventLoop, BindServer server, Object attachment, ServerHandler handler) {
        this.eventLoop = eventLoop;
        this.server = server;
        this.attachment = attachment;
        this.handler = handler;
    }
}
