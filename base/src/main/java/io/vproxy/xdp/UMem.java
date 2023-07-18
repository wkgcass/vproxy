package io.vproxy.xdp;

import io.vproxy.base.util.coll.ConcurrentHashSet;
import io.vproxy.base.util.unsafe.SunUnsafe;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.Set;

public class UMem {
    public final String alias;
    public final long umem;

    public final int chunksSize;
    public final int fillRingSize;
    public final int compRingSize;
    public final int frameSize;
    public final int headroom;

    private MemorySegment memory;
    private long bufferAddress;

    private boolean released = false;
    private boolean referencedBySockets = false;
    private final Set<XDPSocket> referencedSockets = new ConcurrentHashSet<>();

    protected UMem(String alias, long umem, int chunksSize, int fillRingSize, int compRingSize, int frameSize, int headroom) {
        this.alias = alias;
        this.umem = umem;
        this.chunksSize = chunksSize;
        this.fillRingSize = fillRingSize;
        this.compRingSize = compRingSize;
        this.frameSize = frameSize;
        this.headroom = headroom;
    }

    public static UMem create(String alias, int chunksSize, int fillRingSize, int compRingSize,
                              int frameSize, int headroom) throws IOException {
        long umem = NativeXDP.get().createUMem(chunksSize, fillRingSize, compRingSize, frameSize, headroom);
        return new UMem(alias, umem, chunksSize, fillRingSize, compRingSize, frameSize, headroom);
    }

    public MemorySegment getMemory() {
        if (memory != null) {
            return memory;
        }
        memory = NativeXDP.get().getBufferFromUMem(umem).reinterpret((long) chunksSize * (long) frameSize);
        return memory;
    }

    public long getBufferAddress() {
        if (bufferAddress == 0) {
            bufferAddress = NativeXDP.get().getBufferAddressFromUMem(umem);
        }
        return bufferAddress;
    }

    public void fillUpFillRing() {
        NativeXDP.get().fillUpFillRing(umem);
    }

    public Chunk fetchChunk() {
        Chunk chunk = Chunk.fetch();
        if (NativeXDP.get().fetchChunk(umem, chunk)) {
            return chunk;
        } else {
            chunk.returnToPool();
            return null;
        }
    }

    public void release() {
        if (released) {
            return;
        }
        if (!referencedSockets.isEmpty()) {
            throw new IllegalStateException("the umem is referenced by " + referencedSockets.size() + " xsks");
        }
        released = true;
        NativeXDP.get().releaseUMem(umem, memory == null);
        releaseBuffer();
        memory = null;
    }

    // note that this method is overridden by subclass to skip releasing
    protected void releaseBuffer() {
        if (memory != null) {
            SunUnsafe.freeMemory(memory.address());
            memory = null;
        }
    }

    public boolean isReferencedBySockets() {
        return referencedBySockets;
    }

    public boolean isValid() {
        return !released && (!referencedBySockets || !referencedSockets.isEmpty());
    }

    void reference(XDPSocket xsk) {
        referencedBySockets = true;
        referencedSockets.add(xsk);
    }

    void dereference(XDPSocket xsk) {
        referencedSockets.remove(xsk);
    }

    public String toString() {
        return alias + " ->"
            + " chunks " + chunksSize
            + " fill-ring-size " + fillRingSize
            + " comp-ring-size " + compRingSize
            + " frame-size " + frameSize
            + " headroom " + headroom
            + " currently " + (isValid() ? "valid" : "invalid")
            + " current-refs " + referencedSockets;
    }
}
