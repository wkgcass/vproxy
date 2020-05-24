package vproxybase.selector.wrap.kcp;

import vfd.ServerSocketFD;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.selector.wrap.VirtualFD;
import vproxybase.selector.wrap.arqudp.ArqUDPServerSocketFD;
import vproxybase.selector.wrap.udp.ServerDatagramFD;

public class KCPServerSocketFD extends ArqUDPServerSocketFD implements ServerSocketFD, VirtualFD {
    public KCPServerSocketFD(ServerDatagramFD fd, SelectorEventLoop loop, KCPHandler.KCPOptions opts) {
        super(fd, loop, sockFD -> f -> new KCPHandler(f, sockFD, opts));
    }
}
