package vproxy.connection;

public class ConnectionHandlerContext {
    public final NetEventLoop eventLoop;
    public final Connection connection;
    public final Object attachment;
    final ConnectionHandler handler;

    ConnectionHandlerContext(NetEventLoop eventLoop, Connection connection, Object attachment, ConnectionHandler handler) {
        this.eventLoop = eventLoop;
        this.connection = connection;
        this.attachment = attachment;
        this.handler = handler;
    }
}
