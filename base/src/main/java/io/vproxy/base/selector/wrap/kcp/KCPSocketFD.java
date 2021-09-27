package io.vproxy.base.selector.wrap.kcp;

import io.vproxy.base.selector.wrap.arqudp.ArqUDPSocketFD;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.selector.wrap.VirtualFD;
import io.vproxy.base.selector.wrap.arqudp.ArqUDPSocketFD;
import io.vproxy.vfd.SocketFD;

public class KCPSocketFD extends ArqUDPSocketFD implements SocketFD, VirtualFD {
    public KCPSocketFD(SocketFD fd, SelectorEventLoop loop, KCPHandler.KCPOptions opts) {
        super(fd, loop, f -> new KCPHandler(f, fd, opts));
    }
}
