package vproxybase.selector.wrap.arqudp;

import vproxybase.selector.SelectorEventLoop;
import vproxybase.selector.wrap.udp.UDPBasedFDs;

import java.io.IOException;

public interface ArqUDPBasedFDs extends UDPBasedFDs {
    ArqUDPServerSocketFD openServerSocketFD(SelectorEventLoop loop) throws IOException;

    ArqUDPSocketFD openSocketFD(SelectorEventLoop loop) throws IOException;
}
