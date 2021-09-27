package io.vproxy.base.connection;

import java.io.IOException;

public interface ConnectionHandler {
    void readable(ConnectionHandlerContext ctx);

    void writable(ConnectionHandlerContext ctx);

    void exception(ConnectionHandlerContext ctx, IOException err);

    void remoteClosed(ConnectionHandlerContext ctx);

    void closed(ConnectionHandlerContext ctx);

    void removed(ConnectionHandlerContext ctx);
}
