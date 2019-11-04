package vfd;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public interface ServerSocketFD extends FD {
    SocketAddress getLocalAddress() throws IOException;

    /**
     * @return the accepted socket or null
     */
    SocketFD accept() throws IOException;

    void bind(InetSocketAddress l4addr) throws IOException;
}
