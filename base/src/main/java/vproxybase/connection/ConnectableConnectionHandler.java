package vproxybase.connection;

public interface ConnectableConnectionHandler extends ConnectionHandler {
    void connected(ConnectableConnectionHandlerContext ctx);
}
