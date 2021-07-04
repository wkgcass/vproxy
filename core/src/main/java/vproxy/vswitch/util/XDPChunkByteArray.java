package vproxy.vswitch.util;

import vproxy.xdp.Chunk;
import vproxy.xdp.XDPSocket;

public class XDPChunkByteArray extends DirectByteArray {
    public final XDPSocket xsk;
    public final Chunk chunk;

    public XDPChunkByteArray(XDPSocket xsk, Chunk chunk) {
        super(xsk.umem.getBuffer(), chunk.addr(), chunk.endaddr() - chunk.addr());
        this.xsk = xsk;
        this.chunk = chunk;
    }

    public void releaseRef() {
        chunk.releaseRef(xsk.umem);
    }
}
