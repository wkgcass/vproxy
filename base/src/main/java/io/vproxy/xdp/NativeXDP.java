package io.vproxy.xdp;

import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.OS;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.panama.Panama;
import io.vproxy.panama.WrappedFunction;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static io.vproxy.panama.Panama.format;

@SuppressWarnings("CodeBlock2Expr")
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

    private static final WrappedFunction loadAndAttachBPFProgramToNic =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_xdp_NativeXDP_loadAndAttachBPFProgramToNic",
            String.class, String.class, String.class, int.class, boolean.class);

    public long loadAndAttachBPFProgramToNic(String filepath, String programName, String nicName,
                                             int mode, // defined in BPFMode
                                             boolean forceAttach) throws IOException {
        try (var arena = Arena.ofConfined()) {
            return loadAndAttachBPFProgramToNic.invoke((h, e) -> {
                h.invokeExact(e, format(filepath, arena), format(programName, arena), format(nicName, arena), mode, forceAttach);
            }).returnLong(IOException.class);
        }
    }

    private static final WrappedFunction detachBPFProgramFromNic =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_xdp_NativeXDP_detachBPFProgramFromNic",
            String.class);

    public void detachBPFProgramFromNic(String nicName) throws IOException {
        try (var arena = Arena.ofConfined()) {
            detachBPFProgramFromNic.invoke((h, e) -> {
                h.invokeExact(e, format(nicName, arena));
            }).returnNothing(IOException.class);
        }
    }

    private static final WrappedFunction findMapByNameInBPF =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_xdp_NativeXDP_findMapByNameInBPF",
            long.class, String.class);

    public long findMapByNameInBPF(long bpfobj, String mapName) throws IOException {
        try (var arena = Arena.ofConfined()) {
            return findMapByNameInBPF.invoke((h, e) -> {
                h.invokeExact(e, bpfobj, format(mapName, arena));
            }).returnLong(IOException.class);
        }
    }

    private static final WrappedFunction createUMem =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_xdp_NativeXDP_createUMem",
            int.class, int.class, int.class, int.class, int.class);

    public long createUMem(int chunksSize, int fillRingSize, int compRingSize,
                           int frameSize, int headroom) throws IOException {
        return createUMem.invoke((h, e) -> {
            h.invokeExact(e, chunksSize, fillRingSize, compRingSize, frameSize, headroom);
        }).returnLong(IOException.class);
    }

    private static final WrappedFunction shareUMem =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_xdp_NativeXDP_shareUMem",
            long.class);

    public long shareUMem(long umem) {
        return shareUMem.invoke((h, e) -> {
            h.invokeExact(e, umem);
        }).returnLong();
    }

    private static final MemoryLayout buf_st = MemoryLayout.structLayout(
        ValueLayout.ADDRESS.withName("buffer"),
        ValueLayout.JAVA_INT.withName("len")
    );

    private static final WrappedFunction getBufferFromUMem =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_xdp_NativeXDP_getBufferFromUMem",
            long.class, buf_st.getClass());

    public MemorySegment getBufferFromUMem(long umem) {
        try (var arena = Arena.ofConfined()) {
            var seg0 = arena.allocate(buf_st.byteSize());
            var seg = getBufferFromUMem.invoke((h, e) -> {
                h.invokeExact(e, umem, seg0);
            }).returnPointer();
            if (seg == null) {
                return null;
            }
            seg = seg.reinterpret(buf_st.byteSize());
            var ret = seg.get(ValueLayout.ADDRESS, 0);
            var len = seg.get(ValueLayout.JAVA_INT, ValueLayout.ADDRESS.byteSize());
            return ret.reinterpret(len);
        }
    }

    private static final WrappedFunction getBufferAddressFromUMem =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_xdp_NativeXDP_getBufferAddressFromUMem",
            long.class);

    public long getBufferAddressFromUMem(long umem) {
        return getBufferAddressFromUMem.invoke((h, e) -> {
            h.invokeExact(e, umem);
        }).returnLong();
    }

    private static final WrappedFunction createXSK =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_xdp_NativeXDP_createXSK",
            String.class, int.class, long.class, int.class, int.class, int.class, boolean.class, int.class, boolean.class);

    public long createXSK(String nicName, int queueId, long umem,
                          int rxRingSize, int txRingSize,
                          int mode, // defined in BPFMode
                          boolean zeroCopy,
                          int busyPollBudget,
                          boolean rxGenChecksum) throws IOException {
        try (var arena = Arena.ofConfined()) {
            return createXSK.invoke((h, e) -> {
                h.invokeExact(e,
                    format(nicName, arena), queueId, umem, rxRingSize, txRingSize,
                    mode, zeroCopy, busyPollBudget, rxGenChecksum);
            }).returnLong(IOException.class);
        }
    }

    private static final WrappedFunction addXSKIntoMap =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_xdp_NativeXDP_addXSKIntoMap",
            long.class, int.class, long.class);

    public void addXSKIntoMap(long map, int key, long xsk) throws IOException {
        addXSKIntoMap.invoke((h, e) -> {
            h.invokeExact(e, map, key, xsk);
        }).returnNothing(IOException.class);
    }

    private static final WrappedFunction addMacIntoMap =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_xdp_NativeXDP_addMacIntoMap",
            long.class, MemorySegment.class, long.class);

    public void addMacIntoMap(long map, byte[] mac, long xsk) throws IOException {
        try (var arena = Arena.ofConfined()) {
            var macSeg = arena.allocate(mac.length);
            for (int i = 0; i < mac.length; ++i) macSeg.set(ValueLayout.JAVA_BYTE, i, mac[i]);
            addMacIntoMap.invoke((h, e) -> {
                h.invokeExact(e, map, macSeg, xsk);
            }).returnNothing(IOException.class);
        }
    }

    private static final WrappedFunction removeMacFromMap =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_xdp_NativeXDP_removeMacFromMap",
            long.class, MemorySegment.class);

    public void removeMacFromMap(long map, byte[] mac) throws IOException {
        try (var arena = Arena.ofConfined()) {
            var macSeg = arena.allocate(mac.length);
            for (int i = 0; i < mac.length; ++i) macSeg.set(ValueLayout.JAVA_BYTE, i, mac[i]);
            removeMacFromMap.invoke((h, e) -> {
                h.invokeExact(e, map, macSeg);
            }).returnNothing(IOException.class);
        }
    }

    private static final WrappedFunction getFDFromXSK =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_xdp_NativeXDP_getFDFromXSK",
            long.class);

    public int getFDFromXSK(long xsk) {
        return getFDFromXSK.invoke((h, e) -> {
            h.invokeExact(e, xsk);
        }).returnInt();
    }

    private static final WrappedFunction fillUpFillRing =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_xdp_NativeXDP_fillUpFillRing",
            long.class);

    public void fillUpFillRing(long umem) {
        fillUpFillRing.invoke((h, e) -> {
            h.invokeExact(e, umem);
        }).returnNothing();
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

    private static final WrappedFunction fetchPackets0 =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_xdp_NativeXDP_fetchPackets0",
            long.class,
            int.class /*capacity*/,
            MemorySegment.class /*umem*/, MemorySegment.class /*chunk*/,
            MemorySegment.class /*ref*/,
            MemorySegment.class /*addr*/, MemorySegment.class /*endaddr*/,
            MemorySegment.class /*pktaddr*/, MemorySegment.class /*pktlen*/);

    private static int fetchPackets0(
        long xsk,
        @SuppressWarnings("SameParameterValue") int capacity,
        MemorySegment /*long[]*/ umem,
        MemorySegment /*long[]*/ chunk,
        MemorySegment /*int[]*/ ref,
        MemorySegment /*int[]*/ addr, MemorySegment /*int[]*/ endaddr,
        MemorySegment /*int[]*/ pktaddr, MemorySegment /*int[]*/ pktlen) {

        return fetchPackets0.invoke((h, e) -> {
            h.invokeExact(e, xsk, capacity, umem, chunk, ref, addr, endaddr, pktaddr, pktlen);
        }).returnInt();
    }

    private static final WrappedFunction rxRelease =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_xdp_NativeXDP_rxRelease",
            long.class, int.class);

    public void rxRelease(long xsk, int cnt) {
        rxRelease.invoke((h, e) -> {
            h.invokeExact(e, xsk, cnt);
        }).returnNothing();
    }

    private static final WrappedFunction writePacket =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_xdp_NativeXDP_writePacket",
            long.class, long.class);

    public boolean writePacket(long xsk, long chunk) {
        return writePacket.invoke((h, e) -> {
            h.invokeExact(e, xsk, chunk);
        }).returnBool();
    }

    private static final WrappedFunction writePackets =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_xdp_NativeXDP_writePackets",
            long.class, int.class, MemorySegment.class);

    public int writePackets(long xsk, int size, MemorySegment chunkPtrs) {
        return writePackets.invoke((h, e) -> {
            h.invokeExact(e, xsk, size, chunkPtrs);
        }).returnInt();
    }

    private static final WrappedFunction completeTx =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_xdp_NativeXDP_completeTx",
            long.class);

    public void completeTx(long xsk) {
        completeTx.invoke((h, e) -> {
            h.invokeExact(e, xsk);
        }).returnNothing();
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

    private static final WrappedFunction fetchChunk0 =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_xdp_NativeXDP_fetchChunk0",
            long.class,
            MemorySegment.class /*umem*/, MemorySegment.class /*chunk*/,
            MemorySegment.class /*ref*/,
            MemorySegment.class /*addr*/, MemorySegment.class /*endaddr*/,
            MemorySegment.class /*pktaddr*/, MemorySegment.class /*pktlen*/);

    private static boolean fetchChunk0(
        long umemPtr,
        MemorySegment /*long[]*/ umem, MemorySegment /*long[]*/ chunk,
        MemorySegment /*int[]*/ ref,
        MemorySegment /*int[]*/ addr, MemorySegment /*int[]*/ endaddr,
        MemorySegment /*int[]*/ pktaddr, MemorySegment /*int[]*/ pktlen) {

        return fetchChunk0.invoke((h, e) -> {
            h.invokeExact(e, umemPtr, umem, chunk, ref, addr, endaddr, pktaddr, pktlen);
        }).returnBool();
    }

    private static final WrappedFunction setChunk =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_xdp_NativeXDP_setChunk",
            long.class, int.class, int.class, int.class);

    public void setChunk(long chunk, int pktaddr, int pktlen, int csumFlags) {
        setChunk.invoke((h, e) -> {
            h.invokeExact(e, chunk, pktaddr, pktlen, csumFlags);
        }).returnNothing();
    }

    private static final WrappedFunction releaseChunk =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_xdp_NativeXDP_releaseChunk",
            long.class, long.class);

    public void releaseChunk(long umem, long chunk) {
        releaseChunk.invoke((h, e) -> {
            h.invokeExact(e, umem, chunk);
        }).returnNothing();
    }

    private static final WrappedFunction addChunkRefCnt =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_xdp_NativeXDP_addChunkRefCnt",
            long.class);

    public void addChunkRefCnt(long chunk) {
        addChunkRefCnt.invoke((h, e) -> {
            h.invokeExact(e, chunk);
        }).returnNothing();
    }

    private static final WrappedFunction releaseXSK =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_xdp_NativeXDP_releaseXSK",
            long.class);

    public void releaseXSK(long xsk) {
        releaseXSK.invoke((h, e) -> {
            h.invokeExact(e, xsk);
        }).returnNothing();
    }

    private static final WrappedFunction releaseUMem =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_xdp_NativeXDP_releaseUMem",
            long.class, boolean.class);

    public void releaseUMem(long umem, boolean releaseBuffer) {
        releaseUMem.invoke((h, e) -> {
            h.invokeExact(e, umem, releaseBuffer);
        }).returnNothing();
    }

    private static final WrappedFunction releaseBPFObject =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_xdp_NativeXDP_releaseBPFObject",
            long.class);

    public void releaseBPFObject(long bpfobj) {
        releaseBPFObject.invoke((h, e) -> {
            h.invokeExact(e, bpfobj);
        }).returnNothing();
    }
}
