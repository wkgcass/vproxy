package vproxy.base.selector.wrap.kcp;

import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.selector.wrap.VirtualFD;
import vproxy.base.selector.wrap.arqudp.ArqUDPSocketFD;
import vproxy.vfd.SocketFD;

public class KCPSocketFD extends ArqUDPSocketFD implements SocketFD, VirtualFD {
    public KCPSocketFD(SocketFD fd, SelectorEventLoop loop, KCPHandler.KCPOptions opts) {
        super(fd, loop, f -> new KCPHandler(f, fd, opts));
    }
}
