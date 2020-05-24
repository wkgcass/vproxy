package vproxybase.selector.wrap.kcp;

import vfd.SocketFD;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.selector.wrap.VirtualFD;
import vproxybase.selector.wrap.arqudp.ArqUDPSocketFD;

public class KCPSocketFD extends ArqUDPSocketFD implements SocketFD, VirtualFD {
    public KCPSocketFD(SocketFD fd, SelectorEventLoop loop, KCPHandler.KCPOptions opts) {
        super(fd, loop, f -> new KCPHandler(f, fd, opts));
    }
}
