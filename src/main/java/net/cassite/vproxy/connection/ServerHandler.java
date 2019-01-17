package net.cassite.vproxy.connection;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public interface ServerHandler {
    void acceptFail(ServerHandlerContext ctx, IOException err);

    void connection(ServerHandlerContext ctx, Connection connection);

    Connection getConnection(SocketChannel channel);
}
