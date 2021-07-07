package vproxy.xdp;

import vproxy.base.util.objectpool.PrototypeObjectList;
import vproxy.base.util.thread.VProxyThread;

public class ChunkPrototypeObjectList extends PrototypeObjectList<Chunk> {
    public ChunkPrototypeObjectList(int capacity) {
        super(capacity, Chunk::fetch);
    }

    private void add(long umem, long chunk, int ref, int addr, int endaddr, int pktaddr, int pktlen) {
        add().set(umem, chunk, ref, addr, endaddr, pktaddr, pktlen);
    }

    public void add(int count) {
        if (count <= 0) {
            return;
        }
        var variables = VProxyThread.current();
        for (int i = 0; i < count; ++i) {
            add(
                variables.XDPChunk_umemArray[i],
                variables.XDPChunk_chunkArray[i],
                variables.XDPChunk_refArray[i],
                variables.XDPChunk_addrArray[i],
                variables.XDPChunk_endaddrArray[i],
                variables.XDPChunk_pktaddrArray[i],
                variables.XDPChunk_pktlenArray[i]
            );
        }
    }
}
