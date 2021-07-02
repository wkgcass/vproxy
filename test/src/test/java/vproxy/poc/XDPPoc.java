package vproxy.poc;

import vproxy.base.selector.Handler;
import vproxy.base.selector.HandlerContext;
import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.util.ByteArray;
import vproxy.base.util.LogType;
import vproxy.base.util.Logger;
import vproxy.base.util.unsafe.SunUnsafe;
import vproxy.vfd.EventSet;
import vproxy.vpacket.EthernetPacket;
import vproxy.xdp.*;

import java.io.IOException;
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
        var umem = UMem.create(5, 4, 4, 4096, 0);
        var buf = umem.getBuffer();
        Logger.alert("buffer from umem: " + buf);
        var xsk = XDPSocket.create(ifname, 0, umem, 4, 4, BPFMode.SKB, false);
        map.put(0, xsk);

        bpfobj.release();

        Logger.alert("ready to poll");

        var loop = SelectorEventLoop.open();
        int total = 20;
        int[] cnt = {0};
        Logger.alert("this program will recieve " + total + " packets and then exit");
        loop.add(xsk, EventSet.read(), null, new Handler<>() {
            private void handleReadable() {
                List<Chunk> chunks = xsk.fetchPackets();
                for (Chunk chunk : chunks) {
                    Logger.alert("received packet: cnt=" + (++cnt[0]) + ", chunk=" + chunk);
                    chunk.setPositionAndLimit(buf);

                    ByteArray arr = ByteArray.from(buf);
                    Logger.alert("received packet:");
                    Logger.printBytes(arr.toJavaArray());

                    EthernetPacket pkt = new EthernetPacket();
                    String err = pkt.from(arr);
                    if (err != null) {
                        Logger.alert("received invalid packet: " + err);
                        chunk.releaseRef(umem);
                        continue;
                    }
                    Logger.alert("received pkt: " + pkt.description());

                    buf.limit(buf.capacity()); // will modify any place in the buffer

                    for (int i = 0; i < 6; ++i) {
                        byte b = buf.get(chunk.pktaddr + i);
                        buf.put(chunk.pktaddr + i, buf.get(chunk.pktaddr + 6 + i));
                        buf.put(chunk.pktaddr + 6 + i, b);
                    }
                    if (cnt[0] % 2 == 0) {
                        Logger.alert("echo the packet without copying");
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
                        chunk2.pktaddr = chunk2.addr;
                        chunk2.pktlen = chunk.pktlen;

                        Logger.alert("new chunk: " + chunk2);

                        for (int i = 0; i < chunk.pktlen; ++i) {
                            buf.put(chunk2.pktaddr + i,
                                buf.get(chunk.pktaddr + i));
                        }
                        xsk.writePacket(chunk2);
                    }

                    chunk.releaseRef(umem);
                    if (cnt[0] == total) {
                        break;
                    }
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

                if (cnt[0] == total) {
                    try {
                        loop.close();
                    } catch (IOException ignore) {
                    }
                }
            }

            @Override
            public void writable(HandlerContext<XDPSocket> ctx) {
                // will not fire
            }

            @Override
            public void removed(HandlerContext<XDPSocket> ctx) {
                if (cnt[0] != total) {
                    Logger.error(LogType.ALERT, "removed: " + ctx.getChannel());
                }
            }
        });

        loop.loop();
        xsk.close();

        umem.release();
        SunUnsafe.invokeCleaner(buf);
    }
}
