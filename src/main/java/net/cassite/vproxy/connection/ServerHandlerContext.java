package net.cassite.vproxy.connection;

public class ServerHandlerContext {
    public final NetEventLoop eventLoop;
    public final Server server;
    public final Object attachment;
    final ServerHandler handler;

    ServerHandlerContext(NetEventLoop eventLoop, Server server, Object attachment, ServerHandler handler) {
        this.eventLoop = eventLoop;
        this.server = server;
        this.attachment = attachment;
        this.handler = handler;
    }
}
