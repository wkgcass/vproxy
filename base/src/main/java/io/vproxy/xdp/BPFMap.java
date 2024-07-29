package io.vproxy.xdp;

import io.vproxy.pni.Allocator;
import io.vproxy.pni.PNIString;
import io.vproxy.vfd.MacAddress;

import java.io.IOException;
import java.lang.foreign.ValueLayout;

public class BPFMap {
    public final String name;
    public final io.vproxy.vpxdp.BPFMap map;
    public final BPFObject bpfObject;

    BPFMap(String name, io.vproxy.vpxdp.BPFMap map, BPFObject bpfObject) {
        this.name = name;
        this.map = map;
        this.bpfObject = bpfObject;
    }

    public void put(int key, XDPSocket xsk) throws IOException {
        int res = map.addXsk(key, xsk.xsk);
        if (res != 0) {
            throw new IOException("failed to add xsk " + xsk.xsk + " to " + this);
        }
    }

    public void put(MacAddress mac, String ifname) throws IOException {
        int res;
        try (var allocator = Allocator.ofConfined()) {
            var macRaw = allocator.allocate(6);
            for (int i = 0; i < 6; ++i) {
                macRaw.set(ValueLayout.JAVA_BYTE, i, mac.bytes.get(i));
            }
            res = map.addMac(macRaw, new PNIString(allocator, ifname));
        }
        if (res != 0) {
            throw new IOException("failed to add mac " + mac + " to " + this);
        }
    }

    public void remove(MacAddress mac) throws IOException {
        int res;
        try (var allocator = Allocator.ofConfined()) {
            var macRaw = allocator.allocate(6);
            for (int i = 0; i < 6; ++i) {
                macRaw.set(ValueLayout.JAVA_BYTE, i, mac.bytes.get(i));
            }
            res = map.removeMac(macRaw);
        }
        if (res != 0) {
            throw new IOException("failed to remove mac " + mac + " from " + this);
        }
    }
}
