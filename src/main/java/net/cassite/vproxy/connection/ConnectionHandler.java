package net.cassite.vproxy.connection;

import java.io.IOException;

public interface ConnectionHandler {
    void readable(ConnectionHandlerContext ctx);

    void writable(ConnectionHandlerContext ctx);

    void exception(ConnectionHandlerContext ctx, IOException err);

    void closed(ConnectionHandlerContext ctx);
}
