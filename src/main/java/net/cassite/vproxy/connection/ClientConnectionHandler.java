package net.cassite.vproxy.connection;

public interface ClientConnectionHandler extends ConnectionHandler {
    void connected(ClientConnectionHandlerContext ctx);
}
