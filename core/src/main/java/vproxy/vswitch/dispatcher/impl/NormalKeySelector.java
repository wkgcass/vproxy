package vproxy.vswitch.dispatcher.impl;

import vproxy.vswitch.dispatcher.BPFMapKeySelector;
import vproxy.xdp.XDPSocket;

public class NormalKeySelector implements BPFMapKeySelector {
    @Override
    public String alias() {
        return "normal";
    }

    @Override
    public int select(XDPSocket xsk) {
        return xsk.queueId;
    }
}
