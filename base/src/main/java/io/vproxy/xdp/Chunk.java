package io.vproxy.xdp;

import io.vproxy.base.util.thread.VProxyThread;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class Chunk {
    private long umem; // ptr
    private long chunk; // ptr
    private int addr;
    private int endaddr;
    private int ref;

    public int pktaddr;
    public int pktlen;

    public int csumFlags = 0;

    public Chunk() {
    }

    public static Chunk fetch() {
        var variables = VProxyThread.current();
        Chunk chunk = variables.XDPChunk_chunkPool.poll();
        chunk.csumFlags = 0;
        return chunk;
    }

    public void set() {
        var variables = VProxyThread.current();
        set(
            variables.XDPChunk_umemArray.get(ValueLayout.JAVA_LONG, 0),
            variables.XDPChunk_chunkArray.get(ValueLayout.JAVA_LONG, 0),
            variables.XDPChunk_refArray.get(ValueLayout.JAVA_INT, 0),
            variables.XDPChunk_addrArray.get(ValueLayout.JAVA_INT, 0),
            variables.XDPChunk_endaddrArray.get(ValueLayout.JAVA_INT, 0),
            variables.XDPChunk_pktaddrArray.get(ValueLayout.JAVA_INT, 0),
            variables.XDPChunk_pktlenArray.get(ValueLayout.JAVA_INT, 0)
        );
    }

    public void set(long umem, long chunk, int ref, int addr, int endaddr, int pktaddr, int pktlen) {
        this.umem = umem;
        this.chunk = chunk;
        this.ref = ref;
        this.addr = addr;
        this.endaddr = endaddr;
        this.pktaddr = pktaddr;
        this.pktlen = pktlen;
    }

    public void updateNative() {
        NativeXDP.get().setChunk(chunk, pktaddr, pktlen, csumFlags);
    }

    public void reference() {
        referenceInNative();
        ++ref;
    }

    public void referenceInNative() {
        NativeXDP.get().addChunkRefCnt(chunk);
    }

    public void releaseRef(UMem umem) {
        releaseRefInNative(umem);
        ref = (ref == 0) ? 0 : ref - 1;
    }

    public void releaseRefInNative(UMem umem) {
        NativeXDP.get().releaseChunk(umem.umem, chunk);
    }

    public void returnToPool() {
        var variables = VProxyThread.current();
        variables.XDPChunk_chunkPool.add(this);
    }

    public MemorySegment makeSlice(MemorySegment seg) {
        return seg.asSlice(pktaddr, pktlen);
    }

    public long umem() {
        return umem;
    }

    public long chunk() {
        return chunk;
    }

    public int addr() {
        return addr;
    }

    public int endaddr() {
        return endaddr;
    }

    public int ref() {
        return ref;
    }

    @Override
    public String toString() {
        return "Chunk{" +
            "chunk=" + chunk +
            ", umem=" + umem +
            ", addr=" + addr +
            ", endaddr=" + endaddr +
            ", ref=" + ref +
            ", pktaddr=" + pktaddr +
            ", pktlen=" + pktlen +
            "}@" + Integer.toHexString(hashCode());
    }
}
