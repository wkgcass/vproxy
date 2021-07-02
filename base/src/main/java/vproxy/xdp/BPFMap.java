package vproxy.xdp;

import java.io.IOException;

public class BPFMap {
    public final String name;
    public final long map;

    BPFMap(String name, long map) {
        this.name = name;
        this.map = map;
    }

    public void put(int key, XDPSocket xsk) throws IOException {
        NativeXDP.get().addXSKIntoMap(map, key, xsk.xsk);
    }
}
