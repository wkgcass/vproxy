package io.vproxy.xdp;

import io.vproxy.base.util.objectpool.CursorList;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.pni.Allocator;
import io.vproxy.pni.PNIString;
import io.vproxy.pni.array.PointerArray;
import io.vproxy.vfd.FD;
import io.vproxy.vfd.posix.PosixFD;
import io.vproxy.vpxdp.ChunkInfo;
import io.vproxy.vpxdp.XDP;
import io.vproxy.vpxdp.XDPConsts;
import io.vproxy.vpxdp.XskInfo;

import java.io.IOException;
import java.net.SocketOption;
import java.util.Collections;
import java.util.List;

public class XDPSocket extends PosixFD implements FD {
    public final String nic;
    public final int queueId;
    public final XskInfo xsk;
    public final UMem umem;
    private boolean isClosed = false;

    private XDPSocket(String nic, int queueId, XskInfo xsk, UMem umem) {
        super(null);
        this.nic = nic;
        this.queueId = queueId;
        this.xsk = xsk;
        this.umem = umem;
        this.fd = xsk.getXsk().fd();
    }

    public static XDPSocket create(String nicName, int queueId, UMem umem,
                                   int rxRingSize, int txRingSize,
                                   BPFMode mode, boolean zeroCopy,
                                   int busyPollBudget,
                                   boolean rxGenChecksum) throws IOException {
        if (!umem.isValid()) {
            throw new IOException("umem " + umem + " is not valid, create a new one instead");
        }
        if (umem.isReferencedByXsk()) {
            var shared = umem.umem.share();
            umem = new UMem(umem.alias, shared, umem.chunksSize, umem.fillRingSize, umem.compRingSize, umem.frameSize, umem.headroom, umem.metaLen);
        }
        XskInfo xsk;
        try (var allocator = Allocator.ofConfined()) {
            int xdpFlags = mode.mode;
            int bindFlags = 0;
            int vpxdpFlags = 0;
            if (zeroCopy) {
                bindFlags |= XDPConsts.XDP_ZEROCOPY;
            } else {
                bindFlags |= XDPConsts.XDP_COPY;
            }
            if (rxGenChecksum) {
                vpxdpFlags |= NativeXDP.VP_XSK_FLAG_RX_GEN_CSUM;
            }
            xsk = XDP.get().createXsk(new PNIString(allocator, nicName), queueId, umem.umem, rxRingSize, txRingSize,
                xdpFlags, bindFlags, busyPollBudget, vpxdpFlags);
        }
        if (xsk == null) {
            if (umem.isReferencedByXsk()) {
                umem.release();
            }
            throw new IOException("failed to create XDP socket");
        }
        XDPSocket sock = new XDPSocket(nicName, queueId, xsk, umem);
        umem.reference(sock);
        return sock;
    }

    private final CursorList<ChunkInfo> packetsReusedList = new CursorList<>();

    public List<ChunkInfo> fetchPackets() {
        if (isClosed) {
            return Collections.emptyList();
        }
        var idxPtr = VProxyThread.current().XDPIdxPtr;
        var chunkPtr = VProxyThread.current().XDPChunkPtr;

        packetsReusedList.clear();
        idxPtr.set(0, -1);
        var rcvd = xsk.fetchPacket(idxPtr, chunkPtr);
        for (int i = 0; i < rcvd; ++i) {
            xsk.fetchPacket(idxPtr, chunkPtr);
            var chunk = new ChunkInfo(chunkPtr.get(0));
            packetsReusedList.add(chunk);
        }
        return packetsReusedList;
    }

    public void rxRelease(int cnt) {
        if (isClosed) {
            return;
        }
        xsk.rxRelease(cnt);
    }

    public int writePackets(int size, PointerArray chunkPtrs) {
        if (isClosed) {
            return 0;
        }
        if (size == 0) {
            return 0;
        }
        return xsk.writePackets(size, chunkPtrs);
    }

    public void completeTx() {
        if (isClosed) {
            return;
        }
        xsk.completeTx();
    }

    @Override
    public boolean isOpen() {
        return !isClosed;
    }

    @Override
    public void configureBlocking(boolean b) throws IOException {
        // do nothing
    }

    @Override
    public <T> void setOption(SocketOption<T> name, T value) throws IOException {
        // do nothing
    }

    @Override
    public void close() throws IOException {
        if (isClosed) {
            return;
        }
        isClosed = true;
        umem.dereference(this);
        xsk.close();
    }

    @Override
    public FD real() {
        return this;
    }

    @Override
    public String toString() {
        return "XDPSocket("
            + nic + "#" + queueId
            + ",fd=" + fd
            + ",closed=" + isClosed
            + ')';
    }
}
