package io.vproxy.xdp;

import io.vproxy.pni.annotation.*;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

@SuppressWarnings("unused")
@Downcall
interface PNIXDPNative {
    @LinkerOption.Critical
    long loadAndAttachBPFProgramToNic(String filepath, String programName, String nicName,
                                      int mode, // defined in BPFMode
                                      boolean forceAttach) throws IOException;

    @LinkerOption.Critical
    void detachBPFProgramFromNic(String nicName) throws IOException;

    @LinkerOption.Critical
    long findMapByNameInBPF(long bpfobj, String mapName) throws IOException;

    @LinkerOption.Critical
    long createUMem(int chunksSize, int fillRingSize, int compRingSize,
                    int frameSize, int headroom) throws IOException;

    @LinkerOption.Critical
    long shareUMem(long umem);

    @LinkerOption.Critical
    byte[] getBufferFromUMem(long umem);

    @LinkerOption.Critical
    long getBufferAddressFromUMem(long umem);

    @LinkerOption.Critical
    long createXSK(String nicName, int queueId, long umem,
                   int rxRingSize, int txRingSize,
                   int mode, // defined in BPFMode
                   boolean zeroCopy,
                   int busyPollBudget,
                   boolean rxGenChecksum) throws IOException;

    @LinkerOption.Critical
    void addXSKIntoMap(long map, int key, long xsk) throws IOException;

    @LinkerOption.Critical
    void addMacIntoMap(long map, @Raw byte[] mac, long xsk) throws IOException;

    @LinkerOption.Critical
    void removeMacFromMap(long map, @Raw byte[] mac) throws IOException;

    @LinkerOption.Critical
    int getFDFromXSK(long xsk);

    @LinkerOption.Critical
    void fillUpFillRing(long umem);

    @LinkerOption.Critical
    int fetchPackets0(
        long xsk,
        @SuppressWarnings("SameParameterValue") int capacity,
        MemorySegment /*long[]*/ umem,
        MemorySegment /*long[]*/ chunk,
        MemorySegment /*int[]*/ ref,
        MemorySegment /*int[]*/ addr, MemorySegment /*int[]*/ endaddr,
        MemorySegment /*int[]*/ pktaddr, MemorySegment /*int[]*/ pktlen);

    @LinkerOption.Critical
    void rxRelease(long xsk, int cnt);

    @LinkerOption.Critical
    boolean writePacket(long xsk, long chunk);

    @LinkerOption.Critical
    int writePackets(long xsk, int size, MemorySegment chunkPtrs);

    @LinkerOption.Critical
    void completeTx(long xsk);

    @LinkerOption.Critical
    boolean fetchChunk0(
        long umemPtr,
        MemorySegment /*long[]*/ umem, MemorySegment /*long[]*/ chunk,
        MemorySegment /*int[]*/ ref,
        MemorySegment /*int[]*/ addr, MemorySegment /*int[]*/ endaddr,
        MemorySegment /*int[]*/ pktaddr, MemorySegment /*int[]*/ pktlen);

    @LinkerOption.Critical
    void setChunk(long chunk, int pktaddr, int pktlen, int csumFlags);

    @LinkerOption.Critical
    void releaseChunk(long umem, long chunk);

    @LinkerOption.Critical
    void addChunkRefCnt(long chunk);

    @LinkerOption.Critical
    void releaseXSK(long xsk);

    @LinkerOption.Critical
    void releaseUMem(long umem, boolean releaseBuffer);

    @LinkerOption.Critical
    void releaseBPFObject(long bpfobj);
}
