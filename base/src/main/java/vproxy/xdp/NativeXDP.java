package vproxy.xdp;

import java.io.IOException;
import java.nio.ByteBuffer;

public class NativeXDP {
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

            System.loadLibrary("bpf");
            System.loadLibrary("vpxdp");
            instance = new NativeXDP();
        }
        return instance;
    }

    public native long loadAndAttachBPFProgramToNic(String filepath, String programName, String nicName,
                                                    int mode, // defined in BPFMode
                                                    boolean forceAttach) throws IOException;

    public native long findMapByNameInBPF(long bpfobj, String mapName) throws IOException;

    public native long createUMem(int chunksSize, int fillRingSize, int compRingSize,
                                  int frameSize, int headroom) throws IOException;

    public native ByteBuffer getBufferFromUMem(long umem);

    public native long createXSK(String nicName, int queueId, long umem,
                                 int rxRingSize, int txRingSize,
                                 int mode, // defined in BPFMode
                                 boolean zeroCopy) throws IOException;

    public native void addXSKIntoMap(long map, int key, long xsk) throws IOException;

    public native int getFDFromXSK(long xsk);

    public native void fillUpFillRing(long umem);

    public native void fetchPackets(long xsk, ChunkPrototypeObjectList list);

    public native void rxRelease(long xsk, int cnt);

    public native boolean writePacket(long xsk, long chunk);

    public native void completeTx(long xsk);

    public native boolean fetchChunk(long umem, Chunk chunk);

    public native void setChunk(long chunk, int pktaddr, int pktlen);

    public native void releaseChunk(long umem, long chunk);

    public native void addChunkRefCnt(long chunk);

    public native void releaseXSK(long xsk);

    public native void releaseUMem(long umem, boolean releaseBuffer);

    public native void releaseBPFObject(long bpfobj);
}
