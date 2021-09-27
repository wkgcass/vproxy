package vproxy.base.selector.wrap.udp;

import vproxy.base.selector.SelectorEventLoop;
import vproxy.vfd.ServerSocketFD;
import vproxy.vfd.SocketFD;

import java.io.IOException;

public interface UDPBasedFDs {
    ServerSocketFD openServerSocketFD(SelectorEventLoop loop) throws IOException;

    SocketFD openSocketFD(SelectorEventLoop loop) throws IOException;
}
