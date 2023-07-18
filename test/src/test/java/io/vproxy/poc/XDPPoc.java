package io.vproxy.poc;

import io.vproxy.base.selector.Handler;
import io.vproxy.base.selector.HandlerContext;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.unsafe.SunUnsafe;
import io.vproxy.vfd.EventSet;
import io.vproxy.vpacket.*;
import io.vproxy.xdp.*;

import java.util.List;

public class XDPPoc {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            Logger.error(LogType.ALERT, "the first argument should be ifname, or use -Difname={...} if you are using gradle to run this poc program");
            return;
        }
        String ifname = args[0];

        var bpfobj = BPFObject.loadAndAttachToNic("./base/src/main/c/xdp/sample_kern.o",
            "xdp_sock", ifname, BPFMode.SKB, true);
        var map = bpfobj.getMap("xsks_map");
        var umem = UMem.create("poc-umem", 64, 32, 32, 4096, 0);
        var umemSeg = umem.getMemory();
        var arr = ByteArray.from(umemSeg);
        Logger.alert("buffer from umem: " + umemSeg);
        var xsk = XDPSocket.create(ifname, 0, umem, 32, 32, BPFMode.SKB, false, 0, true);
        map.put(0, xsk);

        bpfobj.release(false);

        Logger.alert("ready to poll");

        var loop = SelectorEventLoop.open();
        int[] cnt = {0};
        loop.add(xsk, EventSet.read(), null, new Handler<>() {
            private void handleReadable() {
                List<Chunk> chunks = xsk.fetchPackets();
                for (Chunk chunk : chunks) {
                    Logger.alert("received packet: cnt=" + (++cnt[0]) + ", chunk=" + chunk);
                    var seg = chunk.makeSlice(umemSeg);

                    Logger.alert("received packet:");
                    var pktBuf = ByteArray.from(seg);
                    Logger.printBytes(pktBuf.toJavaArray());

                    EthernetPacket pkt = new EthernetPacket();
                    String err = pkt.from(new PacketDataBuffer(pktBuf));
                    if (err != null) {
                        Logger.alert("received invalid packet: " + err);
                        chunk.releaseRef(umem);
                        continue;
                    }
                    Logger.alert("received pkt: " + pkt.description());

                    for (int i = 0; i < 6; ++i) {
                        byte b = arr.get(chunk.pktaddr + i);
                        arr.set(chunk.pktaddr + i, arr.get(chunk.pktaddr + 6 + i));
                        arr.set(chunk.pktaddr + 6 + i, b);
                    }
                    if (pkt.getPacket() instanceof Ipv6Packet ipv6) {
                        for (int j = 0; j < 16; ++j) {
                            byte b2 = arr.get(chunk.pktaddr + 14 + 8 + j);
                            arr.set(chunk.pktaddr + 14 + 8 + j, arr.get(chunk.pktaddr + 14 + 8 + 16 + j));
                            arr.set(chunk.pktaddr + 14 + 8 + 16 + j, b2);
                        }
                        if (ipv6.getPacket() instanceof IcmpPacket icmpPacket) {
                            if (icmpPacket.getType() == Consts.ICMPv6_PROTOCOL_TYPE_ECHO_REQ) {
                                arr.set(chunk.pktaddr + 14 + 40, (byte) Consts.ICMPv6_PROTOCOL_TYPE_ECHO_RESP);
                                ByteArray icmpRaw = icmpPacket.getRawPacket(AbstractPacket.FLAG_CHECKSUM_UNNECESSARY);
                                {
                                    var foo = ipv6.getSrc();
                                    ipv6.setSrc(ipv6.getDst());
                                    ipv6.setDst(foo);
                                }
                                icmpRaw.set(0, (byte) Consts.ICMPv6_PROTOCOL_TYPE_ECHO_RESP);
                            }
                        }
                    }

                    if (cnt[0] % 2 == 0) {
                        Logger.alert("echo the packet without copying");
                        chunk.csumFlags = NativeXDP.VP_CSUM_UP;
                        chunk.reference();
                        xsk.writePacket(chunk);
                    } else {
                        Logger.alert("copy and echo the packet");
                        Chunk chunk2 = umem.fetchChunk();
                        if (chunk2 == null) {
                            Logger.error(LogType.ALERT, "ERR! umem no enough chunks");
                            chunk.releaseRef(umem);
                            continue;
                        }
                        chunk2.pktaddr = chunk2.addr() + Consts.XDP_HEADROOM_DRIVER_RESERVED;
                        chunk2.pktlen = chunk.pktlen;
                        chunk2.csumFlags = NativeXDP.VP_CSUM_UP;

                        Logger.alert("new chunk: " + chunk2);

                        SunUnsafe.copyMemory(
                            umem.getBufferAddress() + chunk2.pktaddr,
                            umem.getBufferAddress() + chunk.pktaddr,
                            chunk.pktlen);
                        xsk.writePacket(chunk2);
                    }

                    chunk.releaseRef(umem);
                }
                xsk.rxRelease(chunks.size());
                chunks.clear();
                umem.fillUpFillRing();
                xsk.completeTx();
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
                handleReadable();
            }

            @Override
            public void writable(HandlerContext<XDPSocket> ctx) {
                // will not fire
            }

            @Override
            public void removed(HandlerContext<XDPSocket> ctx) {
                Logger.error(LogType.ALERT, "removed: " + ctx.getChannel());
            }
        });

        loop.loop();
        xsk.close();

        umem.release();

        Logger.alert("received " + cnt[0] + " packets, exit");
    }
}
