package vproxy.xdp;

import vproxy.base.util.Utils;
import vproxy.base.util.anno.CriticalNative;
import vproxy.base.util.thread.VProxyThread;

import java.io.IOException;
import java.nio.ByteBuffer;

public class NativeXDP {
    public static final int VP_CSUM_NO = 0;
    public static final int VP_CSUM_IP = 1;
    public static final int VP_CSUM_UP = 2;
    public static final int VP_CSUM_ALL = VP_CSUM_IP | VP_CSUM_UP;

    private static NativeXDP instance;

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

            Utils.loadDynamicLibrary("elf");
            Utils.loadDynamicLibrary("bpf");
            Utils.loadDynamicLibrary("vpxdp");
            instance = new NativeXDP();
        }
        return instance;
    }

    public long loadAndAttachBPFProgramToNic(String filepath, String programName, String nicName,
                                             int mode, // defined in BPFMode
                                             boolean forceAttach) throws IOException {
        return loadAndAttachBPFProgramToNic0(filepath, programName, nicName, mode, forceAttach);
    }

    private static native long loadAndAttachBPFProgramToNic0(String filepath, String programName, String nicName,
                                                             int mode, // defined in BPFMode
                                                             boolean forceAttach) throws IOException;

    public void detachBPFProgramFromNic(String nicName) throws IOException {
        detachBPFProgramFromNic0(nicName);
    }

    private static native void detachBPFProgramFromNic0(String nicName) throws IOException;

    public long findMapByNameInBPF(long bpfobj, String mapName) throws IOException {
        return findMapByNameInBPF0(bpfobj, mapName);
    }

    private static native long findMapByNameInBPF0(long bpfobj, String mapName) throws IOException;

    public long createUMem(int chunksSize, int fillRingSize, int compRingSize,
                           int frameSize, int headroom) throws IOException {
        return createUMem0(chunksSize, fillRingSize, compRingSize, frameSize, headroom);
    }

    private static native long createUMem0(int chunksSize, int fillRingSize, int compRingSize,
                                           int frameSize, int headroom) throws IOException;

    public long shareUMem(long umem) {
        return shareUMem0(umem);
    }

    private static native long shareUMem0(long umem);

    public ByteBuffer getBufferFromUMem(long umem) {
        return getBufferFromUMem0(umem);
    }

    private static native ByteBuffer getBufferFromUMem0(long umem);

    public long getBufferAddressFromUMem(long umem) {
        return getBufferAddressFromUMem0(umem);
    }

    private static native long getBufferAddressFromUMem0(long umem);

    public long createXSK(String nicName, int queueId, long umem,
                          int rxRingSize, int txRingSize,
                          int mode, // defined in BPFMode
                          boolean zeroCopy,
                          int busyPollBudget,
                          boolean rxGenChecksum) throws IOException {
        return createXSK0(nicName, queueId, umem, rxRingSize, txRingSize, mode, zeroCopy, busyPollBudget, rxGenChecksum);
    }

    private static native long createXSK0(String nicName, int queueId, long umem,
                                          int rxRingSize, int txRingSize,
                                          int mode, // defined in BPFMode
                                          boolean zeroCopy,
                                          int busyPollBudget,
                                          boolean rxGenChecksum) throws IOException;

    public void addXSKIntoMap(long map, int key, long xsk) throws IOException {
        addXSKIntoMap0(map, key, xsk);
    }

    private static native void addXSKIntoMap0(long map, int key, long xsk) throws IOException;

    public int getFDFromXSK(long xsk) {
        return getFDFromXSK0(xsk);
    }

    private static native int getFDFromXSK0(long xsk);

    public void fillUpFillRing(long umem) {
        fillUpFillRing0(umem);
    }

    @CriticalNative
    private static native void fillUpFillRing0(long umem);

    public void fetchPackets(long xsk, ChunkPrototypeObjectList list) {
        var variables = VProxyThread.current();
        int count = fetchPackets0(xsk,
            variables.XDPChunk_umemArray,
            variables.XDPChunk_chunkArray,
            variables.XDPChunk_refArray,
            variables.XDPChunk_addrArray,
            variables.XDPChunk_endaddrArray,
            variables.XDPChunk_pktaddrArray,
            variables.XDPChunk_pktlenArray);
        list.add(count);
    }

    @CriticalNative
    private static native int fetchPackets0(
        long xsk,
        long[] umem,
        long[] chunk,
        int[] ref,
        int[] addr,
        int[] endaddr,
        int[] pktaddr,
        int[] pktlen);

    public void rxRelease(long xsk, int cnt) {
        rxRelease0(xsk, cnt);
    }

    @CriticalNative
    private static native void rxRelease0(long xsk, int cnt);

    public boolean writePacket(long xsk, long chunk) {
        return writePacket0(xsk, chunk);
    }

    @CriticalNative
    private static native boolean writePacket0(long xsk, long chunk);

    public int writePackets(long xsk, int size, long[] chunkPtrs) {
        return writePackets0(xsk, size, chunkPtrs);
    }

    @CriticalNative
    private static native int writePackets0(long xsk, int size, long[] chunkPtrs);

    public void completeTx(long xsk) {
        completeTx0(xsk);
    }

    @CriticalNative
    private static native void completeTx0(long xsk);

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

    @CriticalNative
    private static native boolean fetchChunk0(
        long umemPtr,
        long[] umem,
        long[] chunk,
        int[] ref,
        int[] addr,
        int[] endaddr,
        int[] pktaddr,
        int[] pktlen);

    public void setChunk(long chunk, int pktaddr, int pktlen, int csumFlags) {
        setChunk0(chunk, pktaddr, pktlen, csumFlags);
    }

    @CriticalNative
    private static native void setChunk0(long chunk, int pktaddr, int pktlen, int csumFlags);

    public void releaseChunk(long umem, long chunk) {
        releaseChunk0(umem, chunk);
    }

    @CriticalNative
    private static native void releaseChunk0(long umem, long chunk);

    public void addChunkRefCnt(long chunk) {
        addChunkRefCnt0(chunk);
    }

    @CriticalNative
    private static native void addChunkRefCnt0(long chunk);

    public void releaseXSK(long xsk) {
        releaseXSK0(xsk);
    }

    private static native void releaseXSK0(long xsk);

    public void releaseUMem(long umem, boolean releaseBuffer) {
        releaseUMem0(umem, releaseBuffer);
    }

    private static native void releaseUMem0(long umem, boolean releaseBuffer);

    public void releaseBPFObject(long bpfobj) {
        releaseBPFObject0(bpfobj);
    }

    private static native void releaseBPFObject0(long bpfobj);
}
