package io.vproxy.vfd;

import java.io.IOException;

public interface SocketFD extends FD, NetworkFD<IPPort> {
    void connect(IPPort l4addr) throws IOException;

    boolean isConnected();

    void shutdownOutput() throws IOException;

    boolean finishConnect() throws IOException;
}
