package vproxy.xdp;

import vproxy.base.util.objectpool.ConcurrentObjectPool;
import vproxy.base.util.thread.VProxyThread;

import java.nio.ByteBuffer;

public class Chunk {
    private static final int chunkPoolSize = 65536;
    private static final ConcurrentObjectPool<Chunk> chunkPool = new ConcurrentObjectPool<>(chunkPoolSize);

    private long umem; // ptr
    private long chunk; // ptr
    private int addr;
    private int endaddr;
    private int ref;

    public int pktaddr;
    public int pktlen;

    private Chunk() {
    }

    public static Chunk fetch() {
        Chunk chunk = chunkPool.poll();
        if (chunk == null) {
            chunk = new Chunk();
        }
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
        NativeXDP.get().setChunk(chunk, pktaddr, pktlen);
    }

    public void reference() {
        NativeXDP.get().addChunkRefCnt(chunk);
        ++ref;
    }

    public void releaseRef(UMem umem) {
        NativeXDP.get().releaseChunk(umem.umem, chunk);
        ref = (ref == 0) ? 0 : ref - 1;
    }

    public void returnToPool() {
        chunkPool.add(this);
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
            ", addr=" + addr +
            ", pktaddr=" + pktaddr +
            ", pktlen=" + pktlen +
            '}';
    }
}
