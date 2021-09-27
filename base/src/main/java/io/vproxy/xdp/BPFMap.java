package io.vproxy.xdp;

import java.io.IOException;

public class BPFMap {
    public final String name;
    public final long map;
    public final BPFObject bpfObject;

    BPFMap(String name, long map, BPFObject bpfObject) {
        this.name = name;
        this.map = map;
        this.bpfObject = bpfObject;
    }

    public void put(int key, XDPSocket xsk) throws IOException {
        NativeXDP.get().addXSKIntoMap(map, key, xsk.xsk);
    }
}
