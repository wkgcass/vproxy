package io.vproxy.xdp;

import io.vproxy.pni.Allocator;
import io.vproxy.pni.PNIString;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vpxdp.XDPConsts;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
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

    public int getInt(MacAddress mac) throws IOException {
        try (var allocator = Allocator.ofConfined()) {
            var key = allocator.allocate(6);
            var value = allocator.allocate(4);
            setMacIntoMemSeg(mac, key);
            int err = map.lookup(key, 6, value, 4, 0);
            if (err != 0) {
                if (err == -XDPConsts.ENOENT) {
                    throw new FileNotFoundException();
                }
                throw new IOException("failed to retrieve value of key " + mac + " from " + this + ", err = " + err);
            }
            return value.get(ValueLayout.JAVA_INT, 0);
        }
    }

    private void setMacIntoMemSeg(MacAddress mac, MemorySegment seg) {
        seg.copyFrom(MemorySegment.ofArray(mac.bytes.toJavaArray()));
    }

    public void put(int key, XDPSocket xsk) throws IOException {
        int err = map.addXsk(key, xsk.xsk);
        if (err != 0) {
            throw new IOException("failed to add xsk " + xsk.xsk + " to " + this + ", err = " + err);
        }
    }

    public void putNetif(MacAddress mac, String ifname) throws IOException {
        int err;
        try (var allocator = Allocator.ofConfined()) {
            var macRaw = allocator.allocate(6);
            setMacIntoMemSeg(mac, macRaw);
            err = map.addMac2Port(macRaw, new PNIString(allocator, ifname));
        }
        if (err != 0) {
            throw new IOException("failed to add mac2port " + mac + " -> " + ifname + " to " + this + ", err = " + err);
        }
    }

    public void putNetif(String ifname) throws IOException {
        int err;
        try (var allocator = Allocator.ofConfined()) {
            err = map.addPort2Dev(new PNIString(allocator, ifname));
        }
        if (err != 0) {
            throw new IOException("failed to add port2dev " + ifname + " -> " + ifname + " to " + this + ", err = " + err);
        }
    }

    public void put(int key, byte value) throws IOException {
        int err;
        try (var allocator = Allocator.ofConfined()) {
            var keyRaw = allocator.allocate(4);
            var valueRaw = allocator.allocate(1);
            keyRaw.set(ValueLayout.JAVA_INT, 0, key);
            valueRaw.set(ValueLayout.JAVA_BYTE, 0, value);
            err = map.update(keyRaw, 4, valueRaw, 1, 0);
        }
        if (err != 0) {
            throw new IOException("failed to add " + key + " => " + value + " to " + this + ", err = " + err);
        }
    }

    public void put(MacAddress mac, byte value) throws IOException {
        int err;
        try (var allocator = Allocator.ofConfined()) {
            var keyRaw = allocator.allocate(6);
            var valueRaw = allocator.allocate(1);
            setMacIntoMemSeg(mac, keyRaw);
            valueRaw.set(ValueLayout.JAVA_BYTE, 0, value);
            err = map.update(keyRaw, 6, valueRaw, 1, 0);
        }
        if (err != 0) {
            throw new IOException("failed to add " + mac + " => " + value + " to " + this + ", err = " + err);
        }
    }

    public void remove(MacAddress mac) throws IOException {
        int err;
        try (var allocator = Allocator.ofConfined()) {
            var macRaw = allocator.allocate(6);
            setMacIntoMemSeg(mac, macRaw);
            err = map.delete(macRaw, 6, 0);
        }
        if (err != 0) {
            throw new IOException("failed to remove mac " + mac + " from " + this + ", err = " + err);
        }
    }
}
