package vproxy.xdp;

import vproxy.vfd.FD;
import vproxy.vfd.posix.PosixFD;

import java.io.IOException;
import java.net.SocketOption;
import java.util.Collections;
import java.util.List;

public class XDPSocket extends PosixFD implements FD {
    public final String nic;
    public final int queueId;
    public final long xsk;
    public final UMem umem;
    private final ChunkPrototypeObjectList list;
    private boolean isClosed = false;

    private XDPSocket(String nic, int queueId, long xsk, UMem umem, int fd, int rxRingSize) {
        super(null);
        this.nic = nic;
        this.queueId = queueId;
        this.xsk = xsk;
        this.umem = umem;
        this.fd = fd;
        this.list = new ChunkPrototypeObjectList(rxRingSize);
    }

    public static XDPSocket create(String nicName, int queueId, UMem umem,
                                   int rxRingSize, int txRingSize,
                                   BPFMode mode, boolean zeroCopy,
                                   int busyPollBudget,
                                   boolean rxGenChecksum) throws IOException {
        if (!umem.isValid()) {
            throw new IOException("umem " + umem + " is not valid, create a new one instead");
        }
        if (umem.isReferencedBySockets()) {
            umem = SharedUMem.share(umem);
        }
        long xsk = NativeXDP.get().createXSK(nicName, queueId, umem.umem, rxRingSize, txRingSize, mode.mode, zeroCopy, busyPollBudget, rxGenChecksum);
        int fd = NativeXDP.get().getFDFromXSK(xsk);
        XDPSocket sock = new XDPSocket(nicName, queueId, xsk, umem, fd, rxRingSize);
        umem.reference(sock);
        return sock;
    }

    public List<Chunk> fetchPackets() {
        if (isClosed) {
            return Collections.emptyList();
        }
        NativeXDP.get().fetchPackets(xsk, list);
        return list;
    }

    public void rxRelease(int cnt) {
        if (isClosed) {
            return;
        }
        NativeXDP.get().rxRelease(xsk, cnt);
    }

    public boolean writePacket(Chunk chunk) {
        if (isClosed) {
            return false;
        }
        chunk.updateNative();
        return NativeXDP.get().writePacket(xsk, chunk.chunk());
    }

    public void completeTx() {
        if (isClosed) {
            return;
        }
        NativeXDP.get().completeTx(xsk);
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
        NativeXDP.get().releaseXSK(xsk);
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
