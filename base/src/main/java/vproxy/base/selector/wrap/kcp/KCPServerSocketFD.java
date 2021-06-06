package vproxy.base.selector.wrap.kcp;

import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.selector.wrap.VirtualFD;
import vproxy.base.selector.wrap.arqudp.ArqUDPServerSocketFD;
import vproxy.base.selector.wrap.udp.ServerDatagramFD;
import vproxy.vfd.ServerSocketFD;

public class KCPServerSocketFD extends ArqUDPServerSocketFD implements ServerSocketFD, VirtualFD {
    public KCPServerSocketFD(ServerDatagramFD fd, SelectorEventLoop loop, KCPHandler.KCPOptions opts) {
        super(fd, loop, sockFD -> f -> new KCPHandler(f, sockFD, opts));
    }
}
