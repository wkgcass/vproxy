package vfd;

import java.io.IOException;

public interface FDs {
    SocketFD openSocketFD() throws IOException;

    ServerSocketFD openServerSocketFD() throws IOException;

    FDSelector openSelector() throws IOException;
}
