package net.cassite.vproxy.component.proxy;

import net.cassite.vproxy.connection.Connection;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public interface ConnectionGen {
    SocketChannel gen(Connection accepted) throws IOException;
}
