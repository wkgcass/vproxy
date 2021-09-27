package io.vproxy.vswitch.dispatcher.impl;

import io.vproxy.vswitch.dispatcher.BPFMapKeySelector;
import io.vproxy.xdp.XDPSocket;

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
