package io.vproxy.vswitch.util;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.base.util.bytearray.MemorySegmentByteArray;
import io.vproxy.xdp.Chunk;
import io.vproxy.xdp.XDPSocket;

public class UMemChunkByteArray extends MemorySegmentByteArray implements ByteArray {
    public final XDPSocket xsk;
    public final Chunk chunk;

    public UMemChunkByteArray(XDPSocket xsk, Chunk chunk) {
        super(xsk.umem.getMemory().asSlice(
            chunk.addr() + Consts.XDP_HEADROOM_DRIVER_RESERVED,
            chunk.endaddr() - chunk.addr() - Consts.XDP_HEADROOM_DRIVER_RESERVED));

        this.xsk = xsk;
        this.chunk = chunk;
    }

    public void releaseRef() {
        chunk.releaseRef(xsk.umem);
        if (chunk.ref() == 0) {
            chunk.returnToPool();
        }
    }

    public void reference() {
        chunk.reference();
    }
}
