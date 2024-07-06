package io.vproxy.fubuki;

import io.vproxy.base.util.ByteArray;
import io.vproxy.pni.PNIRef;
import io.vproxy.vfd.IP;

import java.lang.foreign.MemorySegment;

public class FubukiUpcallImpl implements FubukiUpcall.Interface {
    @Override
    public void onPacket(MemorySegment packet, long len, MemorySegment ctx) {
        var fubuki = PNIRef.<Fubuki>getRef(ctx);
        fubuki.onPacket(ByteArray.from(packet.reinterpret(len)));
    }

    @Override
    public void addAddress(int addr, int netmask, MemorySegment ctx) {
        var fubuki = PNIRef.<Fubuki>getRef(ctx);
        var ip = IP.fromIPv4(IP.ipv4Int2Bytes(addr));
        var mask = IP.fromIPv4(IP.ipv4Int2Bytes(netmask));
        fubuki.addAddress(ip, mask);
    }

    @Override
    public void deleteAddress(int addr, int netmask, MemorySegment ctx) {
        var fubuki = PNIRef.<Fubuki>getRef(ctx);
        if (addr == 0) {
            return;
        }
        var ip = IP.fromIPv4(IP.ipv4Int2Bytes(addr));
        var mask = IP.fromIPv4(IP.ipv4Int2Bytes(netmask));
        fubuki.deleteAddress(ip, mask);
    }
}
