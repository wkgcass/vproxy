package io.vproxy.base.selector.wrap.kcp;

import io.vproxy.base.selector.wrap.arqudp.ArqUDPServerSocketFD;
import io.vproxy.base.selector.wrap.udp.ServerDatagramFD;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.selector.wrap.VirtualFD;
import io.vproxy.base.selector.wrap.arqudp.ArqUDPServerSocketFD;
import io.vproxy.base.selector.wrap.udp.ServerDatagramFD;
import io.vproxy.vfd.ServerSocketFD;

public class KCPServerSocketFD extends ArqUDPServerSocketFD implements ServerSocketFD, VirtualFD {
    public KCPServerSocketFD(ServerDatagramFD fd, SelectorEventLoop loop, KCPHandler.KCPOptions opts) {
        super(fd, loop, sockFD -> f -> new KCPHandler(f, sockFD, opts));
    }
}
