package io.vproxy.vfd;

import java.io.IOException;

public interface FDs {
    SocketFD openSocketFD() throws IOException;

    ServerSocketFD openServerSocketFD() throws IOException;

    DatagramFD openDatagramFD() throws IOException;

    FDSelector openSelector() throws IOException;

    long currentTimeMillis();
}
