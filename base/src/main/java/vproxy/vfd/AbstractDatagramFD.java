package vproxy.vfd;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface AbstractDatagramFD<ADDR extends SockAddr> extends FD, NetworkFD<ADDR> {
    void connect(ADDR l4addr) throws IOException;

    void bind(ADDR l4addr) throws IOException;

    int send(ByteBuffer buf, ADDR remote) throws IOException;

    ADDR receive(ByteBuffer buf) throws IOException;
}
