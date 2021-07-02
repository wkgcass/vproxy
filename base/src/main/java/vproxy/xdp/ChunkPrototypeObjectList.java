package vproxy.xdp;

import vproxy.base.util.objectpool.PrototypeObjectList;

public class ChunkPrototypeObjectList extends PrototypeObjectList<Chunk> {
    public ChunkPrototypeObjectList(int capacity) {
        super(capacity, Chunk::fetch);
    }

    public void add(long umem, long chunk, int ref, int addr, int endaddr, int pktaddr, int pktlen) {
        add().set(umem, chunk, ref, addr, endaddr, pktaddr, pktlen);
    }
}
