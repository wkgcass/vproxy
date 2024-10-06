package io.vproxy.vswitch.iface;

import io.vproxy.base.selector.Handler;
import io.vproxy.base.selector.HandlerContext;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.pni.Allocator;
import io.vproxy.pni.array.PointerArray;
import io.vproxy.vfd.EventSet;
import io.vproxy.vpacket.AbstractPacket;
import io.vproxy.vpxdp.ChunkInfo;
import io.vproxy.vpxdp.XDPConsts;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.util.SwitchUtils;
import io.vproxy.vswitch.util.UMemChunkByteArray;
import io.vproxy.xdp.*;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;

public class XDPIface extends Iface {
    public final String nic;
    public final UMem umem;
    public final XDPParams params;

    private XDPSocket xsk;
    public final int vrf;
    private SelectorEventLoop loop;

    private final Allocator allocator;

    public record BPFInfo(
        BPFObject bpfObject,
        BPFMap xsk, BPFMap mac2port, BPFMap port2dev, BPFMap srcmac2count, BPFMap passmac,
        String mac2portSharedMapGroup
    ) {
        public BPFInfo(BPFObject bpfObject, BPFMap xsk) {
            this(bpfObject, xsk, null, null, null, null, null);
        }
    }

    public record XDPParams(int queueId, int rxRingSize, int txRingSize, BPFMode mode,
                            boolean zeroCopy, int busyPollBudget, boolean rxGenChecksum,
                            boolean pktswOffloaded, boolean csumOffloaded,
                            BPFInfo bpf) {
    }

    public XDPIface(String nic, int vrf, UMem umem, XDPParams params) {
        // check offload
        if (params.pktswOffloaded) {
            if (params.bpf.mac2port == null || params.bpf.srcmac2count == null) {
                throw new IllegalArgumentException("offload is true, but macMap is not provided");
            }
        }

        this.nic = nic;
        this.umem = umem;
        this.params = params;

        this.vrf = vrf;

        this.allocator = Allocator.ofUnsafe();
        this.sendingChunkPointers = new PointerArray(allocator, params.txRingSize);
    }

    @Override
    public void init(IfaceInitParams initParams) throws Exception {
        super.init(initParams);

        this.loop = initParams.loop;

        XDPSocket xsk;
        try {
            xsk = XDPSocket.create(nic, params.queueId, umem, params.rxRingSize, params.txRingSize,
                params.mode, params.zeroCopy, params.busyPollBudget, params.rxGenChecksum);
        } catch (IOException e) {
            Logger.error(LogType.SOCKET_ERROR, "creating xsk of " + nic + "#" + params.queueId + " failed", e);
            throw e;
        }
        this.xsk = xsk;

        try {
            initParams.loop.add(xsk, EventSet.read(), null, new XDPHandler());
        } catch (IOException e) {
            Logger.error(LogType.EVENT_LOOP_ADD_FAIL, "add xsk into loop failed", e);
            throw e;
        }

        params.bpf.xsk.put(params.queueId, xsk);
    }

    private int sendingChunkSize = 0;
    private final PointerArray sendingChunkPointers;

    @Override
    public void sendPacket(PacketBuffer pkb) {
        if (sendingChunkSize == params.txRingSize) {
            completeTx();
        }

        if (pkb.fullbuf instanceof UMemChunkByteArray ub) {
            var chunk = ub.chunk;
            if (chunk.getUmem().MEMORY.address() == umem.umem.MEMORY.address()) {
                assert Logger.lowLevelDebug("directly send packet without copying");

                // modify chunk
                long pktaddr = chunk.getAddr() + pkb.pktOff;
                int pktlen = pkb.pktBuf.length();

                assert Logger.lowLevelDebug("update pktaddr(" + pktaddr + ") and pktlen(" + pktlen + ")");
                chunk.setPktAddr(pktaddr);
                chunk.setPktLen(pktlen);
                chunk.setCsumFlags(SwitchUtils.checksumFlagsFor(pkb.pkt, umem.metaLen, params.csumOffloaded));
                if (chunk.getCsumFlags() != 0) {
                    statistics.incrCsumSkip();
                }
                ub.reference();

                assert Logger.lowLevelDebug("directly write packet " + chunk + " without copying");
                sendingChunkPointers.set(sendingChunkSize++, chunk.MEMORY);

                statistics.incrTxPkts();
                statistics.incrTxBytes(pktlen);

                // stack generated packets won't have the opportunity to call completeTx
                // ensure the packet is transmitted
                if (!pkb.ifaceInput) {
                    completeTx();
                }
                return;
            }
        }
        assert Logger.lowLevelDebug("fetch a new chunk to send the packet");
        var chunk = umem.umem.getChunks().fetch();
        if (chunk == null) {
            assert Logger.lowLevelDebug("packet dropped because there are no free chunks available");
            statistics.incrTxErr();
            return;
        }
        ByteArray pktData = pkb.pkt.getRawPacket(AbstractPacket.FLAG_CHECKSUM_UNNECESSARY);

        long pktaddr = chunk.getAddr() + umem.headroom;
        long availableSpace = chunk.getEndAddr() - chunk.getAddr() - umem.headroom;
        var csumFlags = SwitchUtils.checksumFlagsFor(pkb.pkt, umem.metaLen, params.csumOffloaded);
        if ((csumFlags & XDPConsts.VP_CSUM_XDP_OFFLOAD) != 0) {
            pktaddr += umem.metaLen;
            availableSpace -= umem.metaLen;
        }

        if (pktData.length() > availableSpace) {
            Logger.warn(LogType.ALERT, "umem chunk too small for packet, pkt: " + pktData.length() + ", available: " + availableSpace);
            umem.umem.getChunks().releaseChunk(chunk);
            return;
        }
        chunk.setPktAddr(pktaddr);
        chunk.setPktLen(pktData.length());
        chunk.setCsumFlags(csumFlags);
        if (chunk.getCsumFlags() != 0) {
            statistics.incrCsumSkip();
        }
        var segBuf = MemorySegment.ofAddress(chunk.getUmem().getBuffer().address() + pktaddr).reinterpret(pktData.length());
        pktData.copyInto(ByteArray.from(segBuf), 0, 0, pktData.length());

        sendingChunkPointers.set(sendingChunkSize++, chunk.MEMORY);

        statistics.incrTxPkts();
        statistics.incrTxBytes(pktData.length());

        // stack generated packets won't have the opportunity to call completeTx
        // ensure the packet is transmitted
        if (!pkb.ifaceInput) {
            completeTx();
        }
    }

    @Override
    public void destroy() {
        if (isDestroyed()) {
            return;
        }
        super.destroy();

        if (params.bpf.mac2portSharedMapGroup != null) {
            SharedBPFMapHolder.getInstance().release(params.bpf.mac2portSharedMapGroup);
        }
        params.bpf.bpfObject.release(true);

        var xsk = this.xsk;
        if (xsk == null) {
            allocator.close();
            return;
        }
        this.xsk = null;
        loop.runOnLoop(() -> {
            try {
                loop.remove(xsk);
            } catch (Throwable ignore) {
            }
            try {
                xsk.close();
            } catch (IOException e) {
                Logger.error(LogType.SOCKET_ERROR, "closing xsk failed", e);
            }
            allocator.close();
        });
    }

    public XDPSocket getXsk() {
        return xsk;
    }

    @Override
    public int getLocalSideVrf(int hint) {
        return this.vrf;
    }

    @Override
    public int getOverhead() {
        return 0;
    }

    @Override
    public void completeTx() {
        if (sendingChunkSize > 0) {
            int n = xsk.writePackets(sendingChunkSize, sendingChunkPointers);
            assert Logger.lowLevelDebug("write xdp packets " + sendingChunkSize + ", succeeded = " + n);
            if (n != sendingChunkSize) {
                for (int i = n; i < sendingChunkSize; ++i) {
                    var chunk = new ChunkInfo(sendingChunkPointers.get(i));
                    assert Logger.lowLevelDebug("writing chunk " + chunk + " failed, need to release them manually");
                    umem.umem.getChunks().releaseChunk(chunk);
                }
                statistics.incrTxErr(sendingChunkSize - n);
            }
            sendingChunkSize = 0; // reset
        }
        xsk.completeTx();
        xsk.umem.umem.fillRingFillup();
    }

    @Override
    public String name() {
        return "xdp:" + nic;
    }

    @Override
    protected String toStringExtra() {
        var off = "";
        if (params.pktswOffloaded || params.csumOffloaded) {
            off = ",offload=";
            var ls = new ArrayList<String>();
            if (params.pktswOffloaded) {
                ls.add("pktsw");
            }
            if (params.csumOffloaded) {
                ls.add("csum");
            }
            off += String.join(",", ls);
        }
        return "#q=" + params.queueId + ",umem=" + umem.alias + ",vrf:" + vrf + off;
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

            var ls = ctx.getChannel().fetchPackets();
            if (ls.isEmpty()) {
                Logger.shouldNotHappen("xdp readable " + xsk + " but received nothing");
            } else {
                assert Logger.lowLevelDebug("xdp readable: " + xsk + ", chunks=" + ls.size());
            }
            for (var chunk : ls) {
                var fullBuffer = new UMemChunkByteArray(xsk, chunk);

                var pkb = PacketBuffer.fromEtherBytes(XDPIface.this, vrf, fullBuffer,
                    (int) (chunk.getPktAddr() - chunk.getAddr()),
                    (int) (chunk.getEndAddr() - chunk.getPktAddr() - chunk.getPktLen()));

                String err = pkb.init();
                if (err != null) {
                    assert Logger.lowLevelDebug("got invalid packet: " + err);
                    fullBuffer.dereference();
                    statistics.incrRxErr();
                    continue;
                }

                statistics.incrRxPkts();
                statistics.incrRxBytes(chunk.getPktLen());

                received(pkb);
            }
            ctx.getChannel().rxRelease(ls.size());
            ls.clear();
            callback.alertPacketsArrive(XDPIface.this);
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
            Logger.warn(LogType.CONN_ERROR, "xdp(queue=" + params.queueId + ") removed from loop, it's not handled anymore, need to be closed");
            try {
                xsk.close();
            } catch (IOException e) {
                Logger.shouldNotHappen("closing xsk failed", e);
            }
            callback.alertDeviceDown(XDPIface.this);
        }
    }

    public static class XDPParamsBuilder {
        public XDPParamsBuilder$1 setQueueId(int queueId) {
            return new XDPParamsBuilder$1(queueId);
        }

        public record XDPParamsBuilder$1(int queueId) {
            public XDPParamsBuilder$2 setBPFInfo(BPFInfo bpf) {
                return new XDPParamsBuilder$2(queueId, bpf);
            }
        }
    }

    public static class XDPParamsBuilder$2 {
        private final int queueId;
        private final BPFInfo bpf;

        private int rxRingSize = SwitchUtils.DEFAULT_RX_TX_CHUNKS;
        private int txRingSize = SwitchUtils.DEFAULT_RX_TX_CHUNKS;
        private BPFMode mode = BPFMode.SKB;
        private boolean zeroCopy = false;
        private int busyPollBudget = 0;
        private boolean rxGenChecksum = false;
        private boolean pktswOffloaded = false;
        private boolean csumOffloaded = false;

        public XDPParamsBuilder$2(int queueId, BPFInfo bpf) {
            this.queueId = queueId;
            this.bpf = bpf;
        }

        public XDPParams build() {
            return new XDPParams(
                queueId, rxRingSize, txRingSize, mode,
                zeroCopy, busyPollBudget, rxGenChecksum,
                pktswOffloaded, csumOffloaded,
                bpf);
        }

        public XDPParamsBuilder$2 setRxRingSize(int rxRingSize) {
            this.rxRingSize = rxRingSize;
            return this;
        }

        public XDPParamsBuilder$2 setTxRingSize(int txRingSize) {
            this.txRingSize = txRingSize;
            return this;
        }

        public XDPParamsBuilder$2 setMode(BPFMode mode) {
            this.mode = mode;
            return this;
        }

        public XDPParamsBuilder$2 setZeroCopy(boolean zeroCopy) {
            this.zeroCopy = zeroCopy;
            return this;
        }

        public XDPParamsBuilder$2 setBusyPollBudget(int busyPollBudget) {
            this.busyPollBudget = busyPollBudget;
            return this;
        }

        public XDPParamsBuilder$2 setRxGenChecksum(boolean rxGenChecksum) {
            this.rxGenChecksum = rxGenChecksum;
            return this;
        }

        public XDPParamsBuilder$2 setPktswOffloaded(boolean pktswOffloaded) {
            this.pktswOffloaded = pktswOffloaded;
            return this;
        }

        public XDPParamsBuilder$2 setCsumOffloaded(boolean csumOffloaded) {
            this.csumOffloaded = csumOffloaded;
            return this;
        }
    }
}
