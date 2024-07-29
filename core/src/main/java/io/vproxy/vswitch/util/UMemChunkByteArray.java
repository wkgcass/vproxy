package io.vproxy.vswitch.util;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.bytearray.MemorySegmentByteArray;
import io.vproxy.vpxdp.ChunkInfo;
import io.vproxy.xdp.XDPSocket;

public class UMemChunkByteArray extends MemorySegmentByteArray implements ByteArray {
    public final XDPSocket xsk;
    public final ChunkInfo chunk;

    public UMemChunkByteArray(XDPSocket xsk, ChunkInfo chunk) {
        super(xsk.umem.umem.getBuffer().reinterpret(Long.MAX_VALUE).asSlice(
            chunk.getAddr(),
            chunk.getEndAddr() - chunk.getAddr()));

        this.xsk = xsk;
        this.chunk = chunk;
    }

    public void dereference() {
        xsk.umem.umem.getChunks().releaseChunk(chunk);
    }

    public void reference() {
        chunk.setRef((byte) (chunk.getRef() + 1));
    }
}
