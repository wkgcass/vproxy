package io.vproxy.xdp;

import io.vproxy.pni.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class XDPNative {
    private XDPNative() {
    }

    private static final XDPNative INSTANCE = new XDPNative();

    public static XDPNative get() {
        return INSTANCE;
    }

    private static final MethodHandle loadAndAttachBPFProgramToNicMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_xdp_XDPNative_loadAndAttachBPFProgramToNic", String.class /* filepath */, String.class /* programName */, String.class /* nicName */, int.class /* mode */, boolean.class /* forceAttach */);

    public long loadAndAttachBPFProgramToNic(PNIEnv ENV, PNIString filepath, PNIString programName, PNIString nicName, int mode, boolean forceAttach) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) loadAndAttachBPFProgramToNicMH.invokeExact(ENV.MEMORY, (MemorySegment) (filepath == null ? MemorySegment.NULL : filepath.MEMORY), (MemorySegment) (programName == null ? MemorySegment.NULL : programName.MEMORY), (MemorySegment) (nicName == null ? MemorySegment.NULL : nicName.MEMORY), mode, forceAttach);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnLong();
    }

    private static final MethodHandle detachBPFProgramFromNicMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_xdp_XDPNative_detachBPFProgramFromNic", String.class /* nicName */);

    public void detachBPFProgramFromNic(PNIEnv ENV, PNIString nicName) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) detachBPFProgramFromNicMH.invokeExact(ENV.MEMORY, (MemorySegment) (nicName == null ? MemorySegment.NULL : nicName.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }

    private static final MethodHandle findMapByNameInBPFMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_xdp_XDPNative_findMapByNameInBPF", long.class /* bpfobj */, String.class /* mapName */);

    public long findMapByNameInBPF(PNIEnv ENV, long bpfobj, PNIString mapName) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) findMapByNameInBPFMH.invokeExact(ENV.MEMORY, bpfobj, (MemorySegment) (mapName == null ? MemorySegment.NULL : mapName.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnLong();
    }

    private static final MethodHandle createUMemMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_xdp_XDPNative_createUMem", int.class /* chunksSize */, int.class /* fillRingSize */, int.class /* compRingSize */, int.class /* frameSize */, int.class /* headroom */);

    public long createUMem(PNIEnv ENV, int chunksSize, int fillRingSize, int compRingSize, int frameSize, int headroom) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) createUMemMH.invokeExact(ENV.MEMORY, chunksSize, fillRingSize, compRingSize, frameSize, headroom);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnLong();
    }

    private static final MethodHandle shareUMemMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_xdp_XDPNative_shareUMem", long.class /* umem */);

    public long shareUMem(PNIEnv ENV, long umem) {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) shareUMemMH.invokeExact(ENV.MEMORY, umem);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwLast();
        }
        return ENV.returnLong();
    }

    private static final MethodHandle getBufferFromUMemMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_xdp_XDPNative_getBufferFromUMem", long.class /* umem */);

    public MemorySegment getBufferFromUMem(PNIEnv ENV, long umem) {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) getBufferFromUMemMH.invokeExact(ENV.MEMORY, umem);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwLast();
        }
        var RES_SEG = ENV.returnBuf();
        if (RES_SEG.isNull()) return null;
        return RES_SEG.get();
    }

    private static final MethodHandle getBufferAddressFromUMemMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_xdp_XDPNative_getBufferAddressFromUMem", long.class /* umem */);

    public long getBufferAddressFromUMem(PNIEnv ENV, long umem) {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) getBufferAddressFromUMemMH.invokeExact(ENV.MEMORY, umem);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwLast();
        }
        return ENV.returnLong();
    }

    private static final MethodHandle createXSKMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_xdp_XDPNative_createXSK", String.class /* nicName */, int.class /* queueId */, long.class /* umem */, int.class /* rxRingSize */, int.class /* txRingSize */, int.class /* mode */, boolean.class /* zeroCopy */, int.class /* busyPollBudget */, boolean.class /* rxGenChecksum */);

    public long createXSK(PNIEnv ENV, PNIString nicName, int queueId, long umem, int rxRingSize, int txRingSize, int mode, boolean zeroCopy, int busyPollBudget, boolean rxGenChecksum) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) createXSKMH.invokeExact(ENV.MEMORY, (MemorySegment) (nicName == null ? MemorySegment.NULL : nicName.MEMORY), queueId, umem, rxRingSize, txRingSize, mode, zeroCopy, busyPollBudget, rxGenChecksum);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnLong();
    }

    private static final MethodHandle addXSKIntoMapMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_xdp_XDPNative_addXSKIntoMap", long.class /* map */, int.class /* key */, long.class /* xsk */);

    public void addXSKIntoMap(PNIEnv ENV, long map, int key, long xsk) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) addXSKIntoMapMH.invokeExact(ENV.MEMORY, map, key, xsk);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }

    private static final MethodHandle addMacIntoMapMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_xdp_XDPNative_addMacIntoMap", long.class /* map */, MemorySegment.class /* mac */, long.class /* xsk */);

    public void addMacIntoMap(PNIEnv ENV, long map, MemorySegment mac, long xsk) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) addMacIntoMapMH.invokeExact(ENV.MEMORY, map, (MemorySegment) (mac == null ? MemorySegment.NULL : mac), xsk);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }

    private static final MethodHandle removeMacFromMapMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_xdp_XDPNative_removeMacFromMap", long.class /* map */, MemorySegment.class /* mac */);

    public void removeMacFromMap(PNIEnv ENV, long map, MemorySegment mac) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) removeMacFromMapMH.invokeExact(ENV.MEMORY, map, (MemorySegment) (mac == null ? MemorySegment.NULL : mac));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }

    private static final MethodHandle getFDFromXSKMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_xdp_XDPNative_getFDFromXSK", long.class /* xsk */);

    public int getFDFromXSK(PNIEnv ENV, long xsk) {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) getFDFromXSKMH.invokeExact(ENV.MEMORY, xsk);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwLast();
        }
        return ENV.returnInt();
    }

    private static final MethodHandle fillUpFillRingMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_xdp_XDPNative_fillUpFillRing", long.class /* umem */);

    public void fillUpFillRing(PNIEnv ENV, long umem) {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) fillUpFillRingMH.invokeExact(ENV.MEMORY, umem);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwLast();
        }
    }

    private static final MethodHandle fetchPackets0MH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_xdp_XDPNative_fetchPackets0", long.class /* xsk */, int.class /* capacity */, MemorySegment.class /* umem */, MemorySegment.class /* chunk */, MemorySegment.class /* ref */, MemorySegment.class /* addr */, MemorySegment.class /* endaddr */, MemorySegment.class /* pktaddr */, MemorySegment.class /* pktlen */);

    public int fetchPackets0(PNIEnv ENV, long xsk, int capacity, MemorySegment umem, MemorySegment chunk, MemorySegment ref, MemorySegment addr, MemorySegment endaddr, MemorySegment pktaddr, MemorySegment pktlen) {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) fetchPackets0MH.invokeExact(ENV.MEMORY, xsk, capacity, (MemorySegment) (umem == null ? MemorySegment.NULL : umem), (MemorySegment) (chunk == null ? MemorySegment.NULL : chunk), (MemorySegment) (ref == null ? MemorySegment.NULL : ref), (MemorySegment) (addr == null ? MemorySegment.NULL : addr), (MemorySegment) (endaddr == null ? MemorySegment.NULL : endaddr), (MemorySegment) (pktaddr == null ? MemorySegment.NULL : pktaddr), (MemorySegment) (pktlen == null ? MemorySegment.NULL : pktlen));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwLast();
        }
        return ENV.returnInt();
    }

    private static final MethodHandle rxReleaseMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_xdp_XDPNative_rxRelease", long.class /* xsk */, int.class /* cnt */);

    public void rxRelease(PNIEnv ENV, long xsk, int cnt) {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) rxReleaseMH.invokeExact(ENV.MEMORY, xsk, cnt);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwLast();
        }
    }

    private static final MethodHandle writePacketMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_xdp_XDPNative_writePacket", long.class /* xsk */, long.class /* chunk */);

    public boolean writePacket(PNIEnv ENV, long xsk, long chunk) {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) writePacketMH.invokeExact(ENV.MEMORY, xsk, chunk);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwLast();
        }
        return ENV.returnBool();
    }

    private static final MethodHandle writePacketsMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_xdp_XDPNative_writePackets", long.class /* xsk */, int.class /* size */, MemorySegment.class /* chunkPtrs */);

    public int writePackets(PNIEnv ENV, long xsk, int size, MemorySegment chunkPtrs) {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) writePacketsMH.invokeExact(ENV.MEMORY, xsk, size, (MemorySegment) (chunkPtrs == null ? MemorySegment.NULL : chunkPtrs));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwLast();
        }
        return ENV.returnInt();
    }

    private static final MethodHandle completeTxMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_xdp_XDPNative_completeTx", long.class /* xsk */);

    public void completeTx(PNIEnv ENV, long xsk) {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) completeTxMH.invokeExact(ENV.MEMORY, xsk);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwLast();
        }
    }

    private static final MethodHandle fetchChunk0MH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_xdp_XDPNative_fetchChunk0", long.class /* umemPtr */, MemorySegment.class /* umem */, MemorySegment.class /* chunk */, MemorySegment.class /* ref */, MemorySegment.class /* addr */, MemorySegment.class /* endaddr */, MemorySegment.class /* pktaddr */, MemorySegment.class /* pktlen */);

    public boolean fetchChunk0(PNIEnv ENV, long umemPtr, MemorySegment umem, MemorySegment chunk, MemorySegment ref, MemorySegment addr, MemorySegment endaddr, MemorySegment pktaddr, MemorySegment pktlen) {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) fetchChunk0MH.invokeExact(ENV.MEMORY, umemPtr, (MemorySegment) (umem == null ? MemorySegment.NULL : umem), (MemorySegment) (chunk == null ? MemorySegment.NULL : chunk), (MemorySegment) (ref == null ? MemorySegment.NULL : ref), (MemorySegment) (addr == null ? MemorySegment.NULL : addr), (MemorySegment) (endaddr == null ? MemorySegment.NULL : endaddr), (MemorySegment) (pktaddr == null ? MemorySegment.NULL : pktaddr), (MemorySegment) (pktlen == null ? MemorySegment.NULL : pktlen));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwLast();
        }
        return ENV.returnBool();
    }

    private static final MethodHandle setChunkMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_xdp_XDPNative_setChunk", long.class /* chunk */, int.class /* pktaddr */, int.class /* pktlen */, int.class /* csumFlags */);

    public void setChunk(PNIEnv ENV, long chunk, int pktaddr, int pktlen, int csumFlags) {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) setChunkMH.invokeExact(ENV.MEMORY, chunk, pktaddr, pktlen, csumFlags);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwLast();
        }
    }

    private static final MethodHandle releaseChunkMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_xdp_XDPNative_releaseChunk", long.class /* umem */, long.class /* chunk */);

    public void releaseChunk(PNIEnv ENV, long umem, long chunk) {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) releaseChunkMH.invokeExact(ENV.MEMORY, umem, chunk);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwLast();
        }
    }

    private static final MethodHandle addChunkRefCntMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_xdp_XDPNative_addChunkRefCnt", long.class /* chunk */);

    public void addChunkRefCnt(PNIEnv ENV, long chunk) {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) addChunkRefCntMH.invokeExact(ENV.MEMORY, chunk);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwLast();
        }
    }

    private static final MethodHandle releaseXSKMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_xdp_XDPNative_releaseXSK", long.class /* xsk */);

    public void releaseXSK(PNIEnv ENV, long xsk) {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) releaseXSKMH.invokeExact(ENV.MEMORY, xsk);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwLast();
        }
    }

    private static final MethodHandle releaseUMemMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_xdp_XDPNative_releaseUMem", long.class /* umem */, boolean.class /* releaseBuffer */);

    public void releaseUMem(PNIEnv ENV, long umem, boolean releaseBuffer) {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) releaseUMemMH.invokeExact(ENV.MEMORY, umem, releaseBuffer);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwLast();
        }
    }

    private static final MethodHandle releaseBPFObjectMH = PanamaUtils.lookupPNIFunction(new PNILinkOptions().setCritical(true), "Java_io_vproxy_xdp_XDPNative_releaseBPFObject", long.class /* bpfobj */);

    public void releaseBPFObject(PNIEnv ENV, long bpfobj) {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) releaseBPFObjectMH.invokeExact(ENV.MEMORY, bpfobj);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwLast();
        }
    }
}
// metadata.generator-version: pni 21.0.0.17
// sha256:53185e45f5db252e49324076dc79f4e2ae0c62403cab57918d6ea8294142339e
