package vfd;

import java.io.IOException;
import java.net.SocketAddress;

public interface NetworkFD extends FD {
    SocketAddress getLocalAddress() throws IOException;

    SocketAddress getRemoteAddress() throws IOException;
}
