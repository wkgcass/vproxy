package io.vproxy.xdp;

import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.OS;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.pni.Allocator;
import io.vproxy.pni.PNIString;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

public class NativeXDP {
    public static final int VP_CSUM_NO = 0;
    public static final int VP_CSUM_IP = 1;
    public static final int VP_CSUM_UP = 2;
    public static final int VP_CSUM_ALL = VP_CSUM_IP | VP_CSUM_UP;

    public static final boolean supportUMemReuse = OS.major() >= 5 && OS.minor() >= 10;

    private static NativeXDP instance;

    static {
        try {
            Utils.loadDynamicLibrary("elf");
        } catch (UnsatisfiedLinkError e) {
            Logger.error(LogType.SYS_ERROR, "unable to load libelf, you may need to add startup argument -Djava.library.path=/usr/lib/`uname -m`-linux-gnu");
            throw e;
        }
        Utils.loadDynamicLibrary("bpf");
        Utils.loadDynamicLibrary("vpxdp");
    }

    private NativeXDP() {
    }

    public static NativeXDP get() {
        if (instance != null) {
            return instance;
        }
        synchronized (NativeXDP.class) {
            if (instance != null) {
                return instance;
            }
            instance = new NativeXDP();
        }
        return instance;
    }

    public long loadAndAttachBPFProgramToNic(String filepath, String programName, String nicName,
                                             int mode, // defined in BPFMode
                                             boolean forceAttach) throws IOException {
        try (var allocator = Allocator.ofPooled()) {
            return XDPNative.get().loadAndAttachBPFProgramToNic(VProxyThread.current().getEnv(),
                new PNIString(allocator, filepath),
                new PNIString(allocator, programName),
                new PNIString(allocator, nicName), mode, forceAttach);
        }
    }

    public void detachBPFProgramFromNic(String nicName) throws IOException {
        try (var allocator = Allocator.ofPooled()) {
            XDPNative.get().detachBPFProgramFromNic(VProxyThread.current().getEnv(), new PNIString(allocator, nicName));
        }
    }

    public long findMapByNameInBPF(long bpfobj, String mapName) throws IOException {
        try (var allocator = Allocator.ofPooled()) {
            return XDPNative.get().findMapByNameInBPF(VProxyThread.current().getEnv(),
                bpfobj, new PNIString(allocator, mapName));
        }
    }

    public long createUMem(int chunksSize, int fillRingSize, int compRingSize,
                           int frameSize, int headroom) throws IOException {
        return XDPNative.get().createUMem(VProxyThread.current().getEnv(),
            chunksSize, fillRingSize, compRingSize, frameSize, headroom);
    }

    public long shareUMem(long umem) {
        return XDPNative.get().shareUMem(VProxyThread.current().getEnv(), umem);
    }

    public MemorySegment getBufferFromUMem(long umem) {
        return XDPNative.get().getBufferFromUMem(VProxyThread.current().getEnv(), umem);
    }

    public long getBufferAddressFromUMem(long umem) {
        return XDPNative.get().getBufferAddressFromUMem(VProxyThread.current().getEnv(), umem);
    }

    public long createXSK(String nicName, int queueId, long umem,
                          int rxRingSize, int txRingSize,
                          int mode, // defined in BPFMode
                          boolean zeroCopy,
                          int busyPollBudget,
                          boolean rxGenChecksum) throws IOException {
        try (var allocator = Allocator.ofPooled()) {
            return XDPNative.get().createXSK(VProxyThread.current().getEnv(),
                new PNIString(allocator, nicName), queueId, umem, rxRingSize, txRingSize, mode, zeroCopy, busyPollBudget, rxGenChecksum);
        }
    }

    public void addXSKIntoMap(long map, int key, long xsk) throws IOException {
        XDPNative.get().addXSKIntoMap(VProxyThread.current().getEnv(),
            map, key, xsk);
    }

    public void addMacIntoMap(long map, byte[] mac, long xsk) throws IOException {
        try (var arena = Arena.ofConfined()) {
            XDPNative.get().addMacIntoMap(VProxyThread.current().getEnv(),
                map, arena.allocate(mac.length).copyFrom(MemorySegment.ofArray(mac)), xsk);
        }
    }

    public void removeMacFromMap(long map, byte[] mac) throws IOException {
        try (var arena = Arena.ofConfined()) {
            XDPNative.get().removeMacFromMap(VProxyThread.current().getEnv(),
                map, arena.allocate(mac.length).copyFrom(MemorySegment.ofArray(mac)));
        }
    }

    public int getFDFromXSK(long xsk) {
        return XDPNative.get().getFDFromXSK(VProxyThread.current().getEnv(), xsk);
    }

    public void fillUpFillRing(long umem) {
        XDPNative.get().fillUpFillRing(VProxyThread.current().getEnv(), umem);
    }

    public void fetchPackets(long xsk, ChunkPrototypeObjectList list) {
        var variables = VProxyThread.current();
        int count = fetchPackets0(xsk,
            VProxyThread.VProxyThreadVariable.XDPChunk_arrayLen,
            variables.XDPChunk_umemArray,
            variables.XDPChunk_chunkArray,
            variables.XDPChunk_refArray,
            variables.XDPChunk_addrArray,
            variables.XDPChunk_endaddrArray,
            variables.XDPChunk_pktaddrArray,
            variables.XDPChunk_pktlenArray);
        list.add(count);
    }

    private static int fetchPackets0(
        long xsk,
        @SuppressWarnings("SameParameterValue") int capacity,
        MemorySegment /*long[]*/ umem,
        MemorySegment /*long[]*/ chunk,
        MemorySegment /*int[]*/ ref,
        MemorySegment /*int[]*/ addr, MemorySegment /*int[]*/ endaddr,
        MemorySegment /*int[]*/ pktaddr, MemorySegment /*int[]*/ pktlen) {
        return XDPNative.get().fetchPackets0(VProxyThread.current().getEnv(),
            xsk, capacity, umem, chunk, ref, addr, endaddr, pktaddr, pktlen);
    }

    public void rxRelease(long xsk, int cnt) {
        XDPNative.get().rxRelease(VProxyThread.current().getEnv(),
            xsk, cnt);
    }

    public boolean writePacket(long xsk, long chunk) {
        return XDPNative.get().writePacket(VProxyThread.current().getEnv(),
            xsk, chunk);
    }

    public int writePackets(long xsk, int size, MemorySegment chunkPtrs) {
        return XDPNative.get().writePackets(VProxyThread.current().getEnv(),
            xsk, size, chunkPtrs);
    }

    public void completeTx(long xsk) {
        XDPNative.get().completeTx(VProxyThread.current().getEnv(), xsk);
    }

    public boolean fetchChunk(long umem, Chunk chunk) {
        var variables = VProxyThread.current();
        boolean ret = fetchChunk0(umem,
            variables.XDPChunk_umemArray,
            variables.XDPChunk_chunkArray,
            variables.XDPChunk_refArray,
            variables.XDPChunk_addrArray,
            variables.XDPChunk_endaddrArray,
            variables.XDPChunk_pktaddrArray,
            variables.XDPChunk_pktlenArray);
        if (ret) {
            chunk.set();
        }
        return ret;
    }

    private static boolean fetchChunk0(
        long umemPtr,
        MemorySegment /*long[]*/ umem, MemorySegment /*long[]*/ chunk,
        MemorySegment /*int[]*/ ref,
        MemorySegment /*int[]*/ addr, MemorySegment /*int[]*/ endaddr,
        MemorySegment /*int[]*/ pktaddr, MemorySegment /*int[]*/ pktlen) {
        return XDPNative.get().fetchChunk0(VProxyThread.current().getEnv(),
            umemPtr, umem, chunk, ref, addr, endaddr, pktaddr, pktlen);
    }

    public void setChunk(long chunk, int pktaddr, int pktlen, int csumFlags) {
        XDPNative.get().setChunk(VProxyThread.current().getEnv(),
            chunk, pktaddr, pktlen, csumFlags);
    }

    public void releaseChunk(long umem, long chunk) {
        XDPNative.get().releaseChunk(VProxyThread.current().getEnv(),
            umem, chunk);
    }

    public void addChunkRefCnt(long chunk) {
        XDPNative.get().addChunkRefCnt(VProxyThread.current().getEnv(), chunk);
    }

    public void releaseXSK(long xsk) {
        XDPNative.get().releaseXSK(VProxyThread.current().getEnv(), xsk);
    }

    public void releaseUMem(long umem, boolean releaseBuffer) {
        XDPNative.get().releaseUMem(VProxyThread.current().getEnv(),
            umem, releaseBuffer);
    }

    public void releaseBPFObject(long bpfobj) {
        XDPNative.get().releaseBPFObject(VProxyThread.current().getEnv(), bpfobj);
    }
}
