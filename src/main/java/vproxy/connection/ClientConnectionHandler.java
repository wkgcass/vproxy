package vproxy.connection;

public interface ClientConnectionHandler extends ConnectionHandler {
    void connected(ClientConnectionHandlerContext ctx);
}
