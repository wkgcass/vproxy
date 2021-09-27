package vproxy.vswitch.dispatcher;

import vproxy.xdp.XDPSocket;

public interface BPFMapKeySelector {
    String alias();

    int select(XDPSocket xsk);
}
