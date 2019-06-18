package vproxy.connection;

public class ClientConnectionHandlerContext extends ConnectionHandlerContext {
    public ClientConnection connection;
    ClientConnectionHandler handler;

    ClientConnectionHandlerContext(NetEventLoop eventLoop, ClientConnection connection, Object attachment, ClientConnectionHandler handler) {
        super(eventLoop, connection, attachment, handler);
        this.connection = connection;
        this.handler = handler;
    }
}
