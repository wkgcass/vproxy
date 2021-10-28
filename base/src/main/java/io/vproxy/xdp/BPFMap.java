package io.vproxy.xdp;

import io.vproxy.vfd.MacAddress;

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

    public void put(MacAddress mac, XDPSocket xsk) throws IOException {
        NativeXDP.get().addMacIntoMap(map, mac.bytes.toJavaArray(), xsk.xsk);
    }

    public void remove(MacAddress mac) throws IOException {
        NativeXDP.get().removeMacFromMap(map, mac.bytes.toJavaArray());
    }
}
