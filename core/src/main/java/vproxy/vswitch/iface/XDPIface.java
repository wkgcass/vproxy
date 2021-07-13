package vproxy.vswitch.iface;

import vproxy.base.selector.Handler;
import vproxy.base.selector.HandlerContext;
import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.util.ByteArray;
import vproxy.base.util.LogType;
import vproxy.base.util.Logger;
import vproxy.vfd.EventSet;
import vproxy.vswitch.PacketBuffer;
import vproxy.vswitch.dispatcher.BPFMapKeySelector;
import vproxy.vswitch.util.XDPChunkByteArray;
import vproxy.xdp.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class XDPIface extends AbstractIface implements Iface {
    public final String alias;
    public final String nic;
    public final BPFMap bpfMap;
    public final UMem umem;
    public final int rxRingSize;
    public final int txRingSize;
    public final BPFMode mode;
    public final boolean zeroCopy;
    public final int queueId;

    private XDPSocket xsk;
    public final int vni;
    public final BPFMapKeySelector keySelector;
    private SelectorEventLoop loop;

    public XDPIface(String alias, String nic, BPFMap bpfMap, UMem umem,
                    int queue, int rxRingSize, int txRingSize, BPFMode mode, boolean zeroCopy,
                    int vni, BPFMapKeySelector keySelector) {
        this.alias = alias;
        this.nic = nic;
        this.bpfMap = bpfMap;
        this.umem = umem;
        this.rxRingSize = rxRingSize;
        this.txRingSize = txRingSize;
        this.mode = mode;
        this.zeroCopy = zeroCopy;
        this.queueId = queue;

        this.vni = vni;
        this.keySelector = keySelector;
    }

    @Override
    public void init(IfaceInitParams params) throws Exception {
        super.init(params);

        this.loop = params.loop;

        XDPSocket xsk;
        try {
            xsk = XDPSocket.create(nic, queueId, umem, rxRingSize, txRingSize, mode, zeroCopy);
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

    @Override
    public void sendPacket(PacketBuffer pkb) {
        if (pkb.fullbuf instanceof XDPChunkByteArray) {
            Chunk chunk = ((XDPChunkByteArray) pkb.fullbuf).chunk;
            if (chunk.umem() == umem.umem) {
                assert Logger.lowLevelDebug("directly send packet without copying");

                // modify chunk
                int pktaddr = chunk.addr() + pkb.pktOff;
                int pktlen = pkb.fullbuf.length() - pkb.pktOff - pkb.pad;
                if (pktaddr != chunk.pktaddr || pktlen != chunk.pktlen) {
                    chunk.pktaddr = pktaddr;
                    chunk.pktlen = pktlen;
                    chunk.updateNative();
                }
                chunk.referenceInNative(); // no need to increase ref in java, it can be directly reused

                boolean wResult = xsk.writePacket(chunk);
                assert wResult || Logger.lowLevelDebug("write packet to " + xsk + " failed, probably tx queue is full");
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
        chunk.pktaddr = chunk.addr();
        chunk.pktlen = pktData.length();
        ByteBuffer byteBuffer = umem.getBuffer();
        chunk.setPositionAndLimit(byteBuffer);
        pktData.byteBufferPut(byteBuffer, 0, pktData.length());

        //noinspection RedundantIfStatement
        if (!xsk.writePacket(chunk)) {
            assert Logger.lowLevelDebug("write packet to " + xsk + " failed, probably tx queue is full");
        }

        // the chunk is not used anymore
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
        xsk.completeTx();
    }

    @Override
    public String toString() {
        return "Iface(xdp:" + alias + ",nic=" + nic + "#" + queueId + ",umem=" + umem.alias + ",vni:" + vni + ")";
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
            List<Chunk> ls = ctx.getChannel().fetchPackets();
            for (Chunk chunk : ls) {
                var fullBuffer = new XDPChunkByteArray(xsk, chunk);

                var pkb = PacketBuffer.fromEtherBytes(XDPIface.this, vni, fullBuffer,
                    chunk.pktaddr - chunk.addr(), chunk.endaddr() - chunk.pktaddr - chunk.pktlen);
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
