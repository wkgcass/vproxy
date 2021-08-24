package vproxy.xdp;

import vproxy.base.util.thread.VProxyThread;

import java.nio.ByteBuffer;

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
            variables.XDPChunk_umemArray[0],
            variables.XDPChunk_chunkArray[0],
            variables.XDPChunk_refArray[0],
            variables.XDPChunk_addrArray[0],
            variables.XDPChunk_endaddrArray[0],
            variables.XDPChunk_pktaddrArray[0],
            variables.XDPChunk_pktlenArray[0]
        );
    }

    public void set(long umem, long chunk, int ref, int addr, int endaddr, int pktaddr, int pktlen) {
        this.umem = umem;
        this.chunk = chunk;
        this.addr = addr;
        this.ref = ref;
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

    public void setPositionAndLimit(ByteBuffer buf) {
        buf.limit(pktaddr + pktlen).position(pktaddr);
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
