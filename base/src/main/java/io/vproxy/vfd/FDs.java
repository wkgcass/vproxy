package io.vproxy.vfd;

import java.io.IOException;

public interface FDs {
    SocketFD openSocketFD() throws IOException;

    ServerSocketFD openServerSocketFD() throws IOException;

    DatagramFD openDatagramFD() throws IOException;

    FDSelector openSelector() throws IOException;

    default long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    default long nanoTime() {
        return System.nanoTime();
    }

    boolean isV4V6DualStack();
}
