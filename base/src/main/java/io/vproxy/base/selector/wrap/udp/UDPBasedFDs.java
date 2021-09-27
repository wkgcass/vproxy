package io.vproxy.base.selector.wrap.udp;

import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.vfd.ServerSocketFD;
import io.vproxy.vfd.SocketFD;

import java.io.IOException;

public interface UDPBasedFDs {
    ServerSocketFD openServerSocketFD(SelectorEventLoop loop) throws IOException;

    SocketFD openSocketFD(SelectorEventLoop loop) throws IOException;
}
