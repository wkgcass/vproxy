package vproxy.vswitch.iface;

import vproxy.base.selector.Handler;
import vproxy.base.selector.HandlerContext;
import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.util.ByteArray;
import vproxy.base.util.Consts;
import vproxy.base.util.LogType;
import vproxy.base.util.Logger;
import vproxy.base.util.thread.VProxyThread;
import vproxy.vfd.EventSet;
import vproxy.vswitch.PacketBuffer;
import vproxy.vswitch.dispatcher.BPFMapKeySelector;
import vproxy.vswitch.util.UMemChunkByteArray;
import vproxy.xdp.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class XDPIface extends Iface {
    public final String nic;
    public final BPFMap bpfMap;
    public final UMem umem;
    public final int rxRingSize;
    public final int txRingSize;
    public final BPFMode mode;
    public final boolean zeroCopy;
    public final int busyPollBudget;
    public final int queueId;

    private XDPSocket xsk;
    public final int vni;
    public final BPFMapKeySelector keySelector;
    private SelectorEventLoop loop;

    public XDPIface(String nic, BPFMap bpfMap, UMem umem,
                    int queue, int rxRingSize, int txRingSize, BPFMode mode, boolean zeroCopy,
                    int busyPollBudget,
                    int vni, BPFMapKeySelector keySelector) {
        this.nic = nic;
        this.bpfMap = bpfMap;
        this.umem = umem;
        this.rxRingSize = rxRingSize;
        this.txRingSize = txRingSize;
        this.mode = mode;
        this.zeroCopy = zeroCopy;
        this.busyPollBudget = busyPollBudget;
        this.queueId = queue;

        this.vni = vni;
        this.keySelector = keySelector;

        this.sendingChunkPointers = new long[txRingSize];
    }

    @Override
    public void init(IfaceInitParams params) throws Exception {
        super.init(params);

        this.loop = params.loop;

        XDPSocket xsk;
        try {
            xsk = XDPSocket.create(nic, queueId, umem, rxRingSize, txRingSize, mode, zeroCopy, busyPollBudget);
        } catch (IOException e) {
            Logger.error(LogType.SOCKET_ERROR, "creating xsk of " + nic + "#" + queueId + " failed", e);
            throw e;
        }
        this.xsk = xsk;

        try {
            params.loop.add(xsk, EventSet.read(), null, new XDPHandler());
        } catch (IOException e) {
            Logger.error(LogType.EVENT_LOOP_ADD_FAIL, "add xsk into loop failed", e);
            throw e;
        }

        int key = keySelector.select(xsk);
        bpfMap.put(key, xsk);
    }

    private int sendingChunkSize = 0;
    private final long[] sendingChunkPointers;

    @Override
    public void sendPacket(PacketBuffer pkb) {
        if (sendingChunkSize == txRingSize) {
            assert Logger.lowLevelDebug("unable to send packets more than tx ring size");
            return;
        }

        if (pkb.fullbuf instanceof UMemChunkByteArray) {
            Chunk chunk = ((UMemChunkByteArray) pkb.fullbuf).chunk;
            if (chunk.umem() == umem.umem) {
                assert Logger.lowLevelDebug("directly send packet without copying");

                // modify chunk
                int pktaddr = chunk.addr() + pkb.pktOff + Consts.XDP_HEADROOM_DRIVER_RESERVED;
                int pktlen = pkb.pktBuf.length();
                if (pktaddr != chunk.pktaddr || pktlen != chunk.pktlen) {
                    assert Logger.lowLevelDebug("update pktaddr(" + pktaddr + ") and pktlen(" + pktlen + ")");
                    chunk.pktaddr = pktaddr;
                    chunk.pktlen = pktlen;
                    chunk.updateNative();
                } else {
                    assert Logger.lowLevelDebug("no modification on chunk packet addresses");
                }
                chunk.referenceInNative(); // no need to increase ref in java, only required in native

                assert Logger.lowLevelDebug("directly write packet " + chunk + " without copying");
                sendingChunkPointers[sendingChunkSize++] = chunk.chunk();
                return;
            }
        }
        assert Logger.lowLevelDebug("fetch a new chunk to send the packet");
        Chunk chunk = umem.fetchChunk();
        if (chunk == null) {
            assert Logger.lowLevelDebug("packet dropped because there are no free chunks available");
            return;
        }
        ByteArray pktData = pkb.pkt.getRawPacket();
        int availableSpace = chunk.endaddr() - chunk.addr() - Consts.XDP_HEADROOM_DRIVER_RESERVED;
        if (pktData.length() > availableSpace) {
            Logger.warn(LogType.ALERT, "umem chunk too small for packet, pkt: " + pktData.length() + ", available: " + availableSpace);
            chunk.releaseRef(umem);
            return;
        }
        chunk.pktaddr = chunk.addr() + Consts.XDP_HEADROOM_DRIVER_RESERVED;
        chunk.pktlen = pktData.length();
        ByteBuffer byteBuffer = umem.getBuffer();
        chunk.setPositionAndLimit(byteBuffer);
        pktData.byteBufferPut(byteBuffer, 0, pktData.length());

        chunk.updateNative();
        sendingChunkPointers[sendingChunkSize++] = chunk.chunk();

        // the chunk is not used in java anymore
        chunk.returnToPool();
    }

    @Override
    public void destroy() {
        if (xsk == null) {
            return;
        }
        var xsk = this.xsk;
        this.xsk = null;
        try {
            loop.remove(xsk);
        } catch (Throwable ignore) {
        }
        try {
            xsk.close();
        } catch (IOException e) {
            Logger.error(LogType.SOCKET_ERROR, "closing xsk failed", e);
        }
    }

    @Override
    public int getLocalSideVni(int hint) {
        return this.vni;
    }

    @Override
    public int getOverhead() {
        return 0;
    }

    @Override
    public void completeTx() {
        if (sendingChunkSize > 0) {
            int n = NativeXDP.get().writePackets(xsk.xsk, sendingChunkSize, sendingChunkPointers);
            if (n != sendingChunkSize) {
                for (int i = n; i < sendingChunkSize; ++i) {
                    long ptr = sendingChunkPointers[i];
                    assert Logger.lowLevelDebug("writing chunk " + ptr + " failed, need to release them manually");
                    NativeXDP.get().releaseChunk(umem.umem, ptr);
                }
            }
            sendingChunkSize = 0; // reset
        }
        xsk.completeTx();
        xsk.umem.fillUpFillRing();
    }

    @Override
    public String name() {
        return "xdp:" + nic;
    }

    @Override
    public String toString() {
        return "Iface(xdp:" + nic + "#" + queueId + ",umem=" + umem.alias + ",vni:" + vni + ")";
    }

    private class XDPHandler implements Handler<XDPSocket> {
        private XDPHandler() {
        }

        @Override
        public void accept(HandlerContext<XDPSocket> ctx) {
            // will not fire
        }

        @Override
        public void connected(HandlerContext<XDPSocket> ctx) {
            // will not fire
        }

        @Override
        public void readable(HandlerContext<XDPSocket> ctx) {
            VProxyThread.current().newUuidDebugInfo();

            List<Chunk> ls = ctx.getChannel().fetchPackets();
            if (ls.isEmpty()) {
                Logger.shouldNotHappen("xdp readable " + xsk + " but received nothing");
            } else {
                assert Logger.lowLevelDebug("xdp readable: " + xsk + ", chunks=" + ls.size());
            }
            for (Chunk chunk : ls) {
                var fullBuffer = new UMemChunkByteArray(xsk, chunk);

                var pkb = PacketBuffer.fromEtherBytes(XDPIface.this, vni, fullBuffer,
                    chunk.pktaddr - chunk.addr() - Consts.XDP_HEADROOM_DRIVER_RESERVED,
                    chunk.endaddr() - chunk.pktaddr - chunk.pktlen);
                String err = pkb.init();
                if (err != null) {
                    assert Logger.lowLevelDebug("got invalid packet: " + err);
                    fullBuffer.releaseRef();
                    continue;
                }

                received(pkb);
            }
            ctx.getChannel().rxRelease(ls.size());
            ls.clear();
            callback.alertPacketsArrive();
        }

        @Override
        public void writable(HandlerContext<XDPSocket> ctx) {
            // will not fire
        }

        @Override
        public void removed(HandlerContext<XDPSocket> ctx) {
            if (xsk == null) {
                return;
            }
            Logger.warn(LogType.CONN_ERROR, "xdp(queue=" + queueId + ") removed from loop, it's not handled anymore, need to be closed");
            try {
                xsk.close();
            } catch (IOException e) {
                Logger.shouldNotHappen("closing xsk failed", e);
            }
            callback.alertDeviceDown(XDPIface.this);
        }
    }
}
