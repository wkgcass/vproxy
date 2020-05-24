package vproxybase.connection;

public class ConnectableConnectionHandlerContext extends ConnectionHandlerContext {
    public ConnectableConnection connection;
    ConnectableConnectionHandler handler;

    ConnectableConnectionHandlerContext(NetEventLoop eventLoop, ConnectableConnection connection, Object attachment, ConnectableConnectionHandler handler) {
        super(eventLoop, connection, attachment, handler);
        this.connection = connection;
        this.handler = handler;
    }
}
