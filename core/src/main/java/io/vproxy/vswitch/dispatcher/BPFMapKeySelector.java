package io.vproxy.vswitch.dispatcher;

import io.vproxy.xdp.XDPSocket;

public interface BPFMapKeySelector {
    String alias();

    int select(XDPSocket xsk);
}
