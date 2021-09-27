package vproxy.vfd;

import java.io.IOException;

public interface ServerSocketFD extends FD {
    IPPort getLocalAddress() throws IOException;

    /**
     * @return the accepted socket or null
     */
    SocketFD accept() throws IOException;

    void bind(IPPort l4addr) throws IOException;
}
