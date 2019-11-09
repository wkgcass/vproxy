package vproxy.selector.wrap.kcp;

import vfd.ServerSocketFD;
import vproxy.selector.SelectorEventLoop;
import vproxy.selector.wrap.VirtualFD;
import vproxy.selector.wrap.arqudp.ArqUDPServerSocketFD;
import vproxy.selector.wrap.udp.ServerDatagramFD;

public class KCPServerSocketFD extends ArqUDPServerSocketFD implements ServerSocketFD, VirtualFD {
    public KCPServerSocketFD(ServerDatagramFD fd, SelectorEventLoop loop) {
        super(fd, loop, sockFD -> f -> new KCPHandler(f, sockFD));
    }
}
