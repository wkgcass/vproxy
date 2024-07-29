package io.vproxy.xdp;

import io.vproxy.vpxdp.XDPConsts;

public enum BPFMode {
    SKB(XDPConsts.XDP_FLAGS_SKB_MODE),
    DRIVER(XDPConsts.XDP_FLAGS_DRV_MODE),
    HARDWARE(XDPConsts.XDP_FLAGS_HW_MODE),
    ;
    public final int mode;

    BPFMode(int mode) {
        this.mode = mode;
    }
}
