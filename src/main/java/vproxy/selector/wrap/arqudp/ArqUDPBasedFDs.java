package vproxy.selector.wrap.arqudp;

import vproxy.selector.SelectorEventLoop;
import vproxy.selector.wrap.udp.UDPBasedFDs;

import java.io.IOException;

public interface ArqUDPBasedFDs extends UDPBasedFDs {
    ArqUDPServerSocketFD openServerSocketFD(SelectorEventLoop loop) throws IOException;

    ArqUDPSocketFD openSocketFD(SelectorEventLoop loop) throws IOException;
}
