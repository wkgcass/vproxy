package io.vproxy.fubuki;

import io.vproxy.base.util.ByteArray;
import io.vproxy.pni.PNILinkOptions;
import io.vproxy.pni.PNIRef;
import io.vproxy.pni.PanamaUtils;
import io.vproxy.vfd.IP;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

public class FubukiUpcall {
    private FubukiUpcall() {
    }

    public static final MemorySegment onPacket;
    public static final MemorySegment addAddress;
    public static final MemorySegment deleteAddress;

    static {
        var arena = Arena.ofShared();
        try {
            onPacket = PanamaUtils.defineCFunction(new PNILinkOptions(), arena,
                FubukiUpcall.class.getMethod("onPacket", MemorySegment.class, long.class, MemorySegment.class));
            addAddress = PanamaUtils.defineCFunction(new PNILinkOptions(), arena,
                FubukiUpcall.class.getMethod("addAddress", int.class, int.class, MemorySegment.class));
            deleteAddress = PanamaUtils.defineCFunction(new PNILinkOptions(), arena,
                FubukiUpcall.class.getMethod("deleteAddress", int.class, int.class, MemorySegment.class));
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static void onPacket(MemorySegment packet, long len, MemorySegment ctx) {
        var fubuki = PNIRef.<Fubuki>getRef(ctx);
        fubuki.onPacket(ByteArray.from(packet.reinterpret(len)));
    }

    public static void addAddress(int addr, int netmask, MemorySegment ctx) {
        var fubuki = PNIRef.<Fubuki>getRef(ctx);
        var ip = IP.fromIPv4(IP.ipv4Int2Bytes(addr));
        var mask = IP.fromIPv4(IP.ipv4Int2Bytes(netmask));
        fubuki.addAddress(ip, mask);
    }

    public static void deleteAddress(int addr, int netmask, MemorySegment ctx) {
        var fubuki = PNIRef.<Fubuki>getRef(ctx);
        if (addr == 0) {
            return;
        }
        var ip = IP.fromIPv4(IP.ipv4Int2Bytes(addr));
        var mask = IP.fromIPv4(IP.ipv4Int2Bytes(netmask));
        fubuki.deleteAddress(ip, mask);
    }
}
