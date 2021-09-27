package vproxy.base.selector.wrap.arqudp;

import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.selector.wrap.udp.UDPBasedFDs;

import java.io.IOException;

public interface ArqUDPBasedFDs extends UDPBasedFDs {
    ArqUDPServerSocketFD openServerSocketFD(SelectorEventLoop loop) throws IOException;

    ArqUDPSocketFD openSocketFD(SelectorEventLoop loop) throws IOException;
}
