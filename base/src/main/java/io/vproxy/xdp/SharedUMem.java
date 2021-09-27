package io.vproxy.xdp;

public class SharedUMem extends UMem {
    public final UMem target;

    private SharedUMem(long ptr, UMem umem) {
        super(umem.alias, ptr, umem.chunksSize, umem.fillRingSize, umem.compRingSize, umem.frameSize, umem.headroom);
        this.target = umem;
    }

    public static SharedUMem share(UMem umem) {
        long ptr = NativeXDP.get().shareUMem(umem.umem);
        return new SharedUMem(ptr, umem);
    }

    @Override
    protected void releaseBuffer() {
        // no, do not release
    }

    @Override
    void reference(XDPSocket xsk) {
        super.reference(xsk);
        target.reference(xsk);
    }

    @Override
    void dereference(XDPSocket xsk) {
        super.dereference(xsk);
        target.dereference(xsk);
    }
}
