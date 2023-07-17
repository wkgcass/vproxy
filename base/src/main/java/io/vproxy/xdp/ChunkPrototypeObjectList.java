package io.vproxy.xdp;

import io.vproxy.base.util.objectpool.PrototypeObjectList;
import io.vproxy.base.util.thread.VProxyThread;

import java.lang.foreign.ValueLayout;

public class ChunkPrototypeObjectList extends PrototypeObjectList<Chunk> {
    public ChunkPrototypeObjectList(int capacity) {
        super(capacity, Chunk::fetch);
    }

    @Override
    public Chunk add() {
        var chunk = Chunk.fetch();
        add(chunk);
        return chunk;
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
                variables.XDPChunk_umemArray.get(ValueLayout.JAVA_LONG, 8L * i),
                variables.XDPChunk_chunkArray.get(ValueLayout.JAVA_LONG, 8L * i),
                variables.XDPChunk_refArray.get(ValueLayout.JAVA_INT, 4L * i),
                variables.XDPChunk_addrArray.get(ValueLayout.JAVA_INT, 4L * i),
                variables.XDPChunk_endaddrArray.get(ValueLayout.JAVA_INT, 4L * i),
                variables.XDPChunk_pktaddrArray.get(ValueLayout.JAVA_INT, 4L * i),
                variables.XDPChunk_pktlenArray.get(ValueLayout.JAVA_INT, 4L * i)
            );
        }
    }
}
