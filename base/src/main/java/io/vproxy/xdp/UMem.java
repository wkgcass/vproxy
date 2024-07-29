package io.vproxy.xdp;

import io.vproxy.base.util.coll.ConcurrentHashSet;
import io.vproxy.vpxdp.UMemInfo;
import io.vproxy.vpxdp.XDP;

import java.io.IOException;
import java.util.Set;

public class UMem {
    public final String alias;
    public final UMemInfo umem;

    public final int chunksSize;
    public final int fillRingSize;
    public final int compRingSize;
    public final int frameSize;
    public final int headroom;
    public final int metaLen;

    private boolean released = false;
    private boolean referencedByXsk = false;
    private final Set<XDPSocket> referencedSockets = new ConcurrentHashSet<>();

    UMem(String alias, UMemInfo umem, int chunksSize, int fillRingSize, int compRingSize, int frameSize, int headroom, int metaLen) {
        this.alias = alias;
        this.umem = umem;
        this.chunksSize = chunksSize;
        this.fillRingSize = fillRingSize;
        this.compRingSize = compRingSize;
        this.frameSize = frameSize;
        this.headroom = headroom;
        this.metaLen = metaLen;
    }

    public static UMem create(String alias, int chunksCount, int fillRingSize, int compRingSize,
                              int frameSize, int headroom, int metaLen) throws IOException {
        NativeXDP.load();

        var umem = XDP.get().createUMem(chunksCount, fillRingSize, compRingSize, frameSize, headroom, metaLen);
        if (umem == null) {
            throw new IOException("failed to create UMem");
        }
        return new UMem(alias, umem, chunksCount, fillRingSize, compRingSize, frameSize, headroom, metaLen);
    }

    public void release() {
        if (released) {
            return;
        }
        if (!referencedSockets.isEmpty()) {
            throw new IllegalStateException("the umem is referenced by " + referencedSockets.size() + " xsks");
        }
        synchronized (this) {
            if (released) {
                return;
            }
            released = true;
        }
        umem.close(true);
    }

    public boolean isReferencedByXsk() {
        return referencedByXsk;
    }

    public boolean isValid() {
        return !released && (!referencedByXsk || !referencedSockets.isEmpty());
    }

    void reference(XDPSocket xsk) {
        referencedByXsk = true;
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
            + " meta-len " + metaLen
            + " currently " + (isValid() ? "valid" : "invalid")
            + " current-refs " + referencedSockets;
    }
}
