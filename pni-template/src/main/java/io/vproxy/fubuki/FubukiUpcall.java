package io.vproxy.fubuki;

import io.vproxy.pni.annotation.Upcall;

import java.lang.foreign.MemorySegment;

@Upcall
interface PNIFubukiUpcall {
    void onPacket(MemorySegment packet, long len, MemorySegment ctx);

    void addAddress(int addr, int netmask, MemorySegment ctx);

    void deleteAddress(int addr, int netmask, MemorySegment ctx);
}
