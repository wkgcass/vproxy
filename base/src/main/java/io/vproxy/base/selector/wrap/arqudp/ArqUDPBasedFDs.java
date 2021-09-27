package io.vproxy.base.selector.wrap.arqudp;

import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.selector.wrap.udp.UDPBasedFDs;

import java.io.IOException;

public interface ArqUDPBasedFDs extends UDPBasedFDs {
    ArqUDPServerSocketFD openServerSocketFD(SelectorEventLoop loop) throws IOException;

    ArqUDPSocketFD openSocketFD(SelectorEventLoop loop) throws IOException;
}
