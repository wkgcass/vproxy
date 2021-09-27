package vproxy.vswitch.dispatcher.impl;

import vproxy.vswitch.dispatcher.BPFMapKeySelector;
import vproxy.xdp.XDPSocket;

public class KeySelectorUseQueueId implements BPFMapKeySelector {
    @Override
    public String alias() {
        return "useQueueId";
    }

    @Override
    public int select(XDPSocket xsk) {
        return xsk.queueId;
    }
}
