package io.vproxy.xdp;

import io.vproxy.pni.annotation.Function;
import io.vproxy.pni.annotation.Raw;
import io.vproxy.pni.annotation.Trivial;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

@SuppressWarnings("unused")
@Function
interface PNIXDPNative {
    @Trivial
    long loadAndAttachBPFProgramToNic(String filepath, String programName, String nicName,
                                      int mode, // defined in BPFMode
                                      boolean forceAttach) throws IOException;

    @Trivial
    void detachBPFProgramFromNic(String nicName) throws IOException;

    @Trivial
    long findMapByNameInBPF(long bpfobj, String mapName) throws IOException;

    @Trivial
    long createUMem(int chunksSize, int fillRingSize, int compRingSize,
                    int frameSize, int headroom) throws IOException;

    @Trivial
    long shareUMem(long umem);

    @Trivial
    byte[] getBufferFromUMem(long umem);

    @Trivial
    long getBufferAddressFromUMem(long umem);

    @Trivial
    long createXSK(String nicName, int queueId, long umem,
                   int rxRingSize, int txRingSize,
                   int mode, // defined in BPFMode
                   boolean zeroCopy,
                   int busyPollBudget,
                   boolean rxGenChecksum) throws IOException;

    @Trivial
    void addXSKIntoMap(long map, int key, long xsk) throws IOException;

    @Trivial
    void addMacIntoMap(long map, @Raw byte[] mac, long xsk) throws IOException;

    @Trivial
    void removeMacFromMap(long map, @Raw byte[] mac) throws IOException;

    @Trivial
    int getFDFromXSK(long xsk);

    @Trivial
    void fillUpFillRing(long umem);

    @Trivial
    int fetchPackets0(
        long xsk,
        @SuppressWarnings("SameParameterValue") int capacity,
        MemorySegment /*long[]*/ umem,
        MemorySegment /*long[]*/ chunk,
        MemorySegment /*int[]*/ ref,
        MemorySegment /*int[]*/ addr, MemorySegment /*int[]*/ endaddr,
        MemorySegment /*int[]*/ pktaddr, MemorySegment /*int[]*/ pktlen);

    @Trivial
    void rxRelease(long xsk, int cnt);

    @Trivial
    boolean writePacket(long xsk, long chunk);

    @Trivial
    int writePackets(long xsk, int size, MemorySegment chunkPtrs);

    @Trivial
    void completeTx(long xsk);

    @Trivial
    boolean fetchChunk0(
        long umemPtr,
        MemorySegment /*long[]*/ umem, MemorySegment /*long[]*/ chunk,
        MemorySegment /*int[]*/ ref,
        MemorySegment /*int[]*/ addr, MemorySegment /*int[]*/ endaddr,
        MemorySegment /*int[]*/ pktaddr, MemorySegment /*int[]*/ pktlen);

    @Trivial
    void setChunk(long chunk, int pktaddr, int pktlen, int csumFlags);

    @Trivial
    void releaseChunk(long umem, long chunk);

    @Trivial
    void addChunkRefCnt(long chunk);

    @Trivial
    void releaseXSK(long xsk);

    @Trivial
    void releaseUMem(long umem, boolean releaseBuffer);

    @Trivial
    void releaseBPFObject(long bpfobj);
}
