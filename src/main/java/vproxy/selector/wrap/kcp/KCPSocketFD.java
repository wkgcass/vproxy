package vproxy.selector.wrap.kcp;

import vfd.SocketFD;
import vproxy.selector.SelectorEventLoop;
import vproxy.selector.wrap.VirtualFD;
import vproxy.selector.wrap.arqudp.ArqUDPSocketFD;

public class KCPSocketFD extends ArqUDPSocketFD implements SocketFD, VirtualFD {
    public KCPSocketFD(SocketFD fd, SelectorEventLoop loop, KCPHandler.KCPOptions opts) {
        super(fd, loop, f -> new KCPHandler(f, fd, opts));
    }
}
