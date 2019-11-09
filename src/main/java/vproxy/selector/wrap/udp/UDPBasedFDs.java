package vproxy.selector.wrap.udp;

import vfd.ServerSocketFD;
import vfd.SocketFD;
import vproxy.selector.SelectorEventLoop;

import java.io.IOException;

public interface UDPBasedFDs {
    ServerSocketFD openServerSocketFD(SelectorEventLoop loop) throws IOException;

    SocketFD openSocketFD(SelectorEventLoop loop) throws IOException;
}
