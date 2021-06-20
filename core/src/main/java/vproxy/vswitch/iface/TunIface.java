package vproxy.vswitch.iface;

import vproxy.base.selector.Handler;
import vproxy.base.selector.HandlerContext;
import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.selector.wrap.blocking.BlockingDatagramFD;
import vproxy.base.util.*;
import vproxy.base.util.exception.XException;
import vproxy.base.util.thread.VProxyThread;
import vproxy.vfd.*;
import vproxy.vpacket.AbstractIpPacket;
import vproxy.vpacket.ArpPacket;
import vproxy.vpacket.IcmpPacket;
import vproxy.vpacket.Ipv4Packet;
import vproxy.vswitch.PacketBuffer;
import vproxy.vswitch.util.SwitchUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class TunIface extends AbstractIface implements Iface {
    private static final ByteArray icmpPingOtherPart = ByteArray.from("vpsr"); // id and seq only, no data

    public final String devPattern;
    private TapDatagramFD tun;
    public final int localSideVni;
    public final MacAddress mac;
    public final String postScript;
    public final Annotations annotations;

    private AbstractDatagramFD<?> operateTun;
    private SelectorEventLoop bondLoop;

    private final ByteBuffer sndBuf = ByteBuffer.allocateDirect(2048);

    public TunIface(String devPattern,
                    int localSideVni,
                    MacAddress mac,
                    String postScript,
                    Annotations annotations) {
        this.devPattern = devPattern;
        this.localSideVni = localSideVni;
        this.mac = mac;
        this.postScript = postScript;
        if (annotations == null) {
            annotations = new Annotations();
        }
        this.annotations = annotations;
    }

    public TapDatagramFD getTun() {
        return tun;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TunIface tunIface = (TunIface) o;
        return Objects.equals(tun, tunIface.tun);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tun);
    }

    @Override
    public String toString() {
        return "Iface(tun:" + tun.getTap().dev + ",vni:" + localSideVni + ",mac:" + mac + ")";
    }

    @Override
    public void init(IfaceInitParams params) throws Exception {
        super.init(params);

        bondLoop = params.loop;
        FDs fds = FDProvider.get().getProvided();
        FDsWithTap tapFDs = (FDsWithTap) fds;
        tun = tapFDs.openTun(devPattern);
        try {
            if (tapFDs.tunNonBlockingSupported()) {
                operateTun = tun;
                tun.configureBlocking(false);
            } else {
                operateTun = new BlockingDatagramFD<>(tun, bondLoop, 2048, 65536, 32);
            }
            bondLoop.add(operateTun, EventSet.read(), null, new TunHandler());
        } catch (IOException e) {
            if (operateTun != null) {
                try {
                    operateTun.close();
                    operateTun = null;
                } catch (IOException t) {
                    Logger.shouldNotHappen("failed to close the tun device wrapper when rolling back the creation", t);
                }
            }
            try {
                tun.close();
            } catch (IOException t) {
                Logger.shouldNotHappen("failed to close the tun device when rolling back the creation", t);
            }
            throw e;
        }

        try {
            SwitchUtils.executeDevPostScript(params.sw.alias, tun.getTap().dev, localSideVni, postScript);
        } catch (Exception e) {
            // executing script failed
            // close the fds
            try {
                bondLoop.remove(operateTun);
            } catch (Throwable ignore) {
            }
            try {
                operateTun.close();
                operateTun = null;
            } catch (IOException t) {
                Logger.shouldNotHappen("failed to close the tun device wrapper when rolling back the creation", t);
            }
            try {
                tun.close();
            } catch (IOException t) {
                Logger.shouldNotHappen("closing the tun fd failed, " + tun, t);
            }
            throw new XException(Utils.formatErr(e));
        }
    }

    @Override
    public void sendPacket(PacketBuffer pkb) {
        if (handleArpOrNdp(pkb)) {
            assert Logger.lowLevelDebug("the packet is arp/ndp req, which is handled another way, " +
                "original packet will not be sent");
            return;
        }
        if (pkb.ipPkt == null) {
            assert Logger.lowLevelDebug("packet is not sent to " + this + " because there is no ip packet");
            return;
        }

        sendPacket(pkb.ipPkt);
    }

    private void sendPacket(AbstractIpPacket ipPkt) {
        assert Logger.lowLevelDebug(this + ".sendPacket(" + ipPkt + ")");

        sndBuf.position(0).limit(sndBuf.capacity());
        var bytes = ipPkt.getRawPacket().toJavaArray();
        if (OS.isMac()) { // add AF header for macos
            if (ipPkt instanceof Ipv4Packet) {
                sndBuf.putInt(Consts.AF_INET);
            } else {
                sndBuf.putInt(Consts.AF_INET6);
            }
        }
        sndBuf.put(bytes);
        sndBuf.flip();
        try {
            operateTun.write(sndBuf);
        } catch (IOException e) {
            Logger.error(LogType.SOCKET_ERROR, "sending packet to " + this + " failed", e);
        }
    }

    private boolean handleArpOrNdp(PacketBuffer pkb) {
        if (pkb.pkt.getDst().isUnicast() && !pkb.pkt.getDst().equals(mac)) {
            assert Logger.lowLevelDebug("unicast packet whose dst is not this dev");
            return false;
        }
        assert Logger.lowLevelDebug("try to handle arp or ndp for tun dev");
        if (pkb.ipPkt != null && pkb.ipPkt.getPacket() instanceof IcmpPacket) {
            assert Logger.lowLevelDebug("is icmp");
            var icmp = (IcmpPacket) pkb.ipPkt.getPacket();
            if (icmp.getType() != Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Solicitation) {
                assert Logger.lowLevelDebug("is not neighbor solicitation");
                return false;
            }
            IP dst = SwitchUtils.extractTargetAddressFromNeighborSolicitation(icmp);
            if (dst == null) {
                assert Logger.lowLevelDebug("invalid neighbor solicitation");
                return false;
            }
            IP src = pkb.ipPkt.getSrc();
            buildAndSendPing(src, dst);
        } else if (pkb.pkt.getPacket() instanceof ArpPacket) {
            assert Logger.lowLevelDebug("is arp");
            var arp = (ArpPacket) pkb.pkt.getPacket();
            if (arp.getOpcode() != Consts.ARP_PROTOCOL_OPCODE_REQ) {
                assert Logger.lowLevelDebug("is not arp request");
                return false;
            }
            if (arp.getProtocolType() != Consts.ARP_PROTOCOL_TYPE_IP) {
                assert Logger.lowLevelDebug("protocol type is not ip");
                return false;
            }
            int len = arp.getProtocolSize();
            if (len != 4 && len != 16) {
                assert Logger.lowLevelDebug("protocol size is not 4 nor 16");
                return false;
            }
            handleArp(pkb);
        }
        assert Logger.lowLevelDebug("not arp nor neighbor solicitation");
        return false;
    }

    private void handleArp(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("handling arp for tun dev");
        var arp = (ArpPacket) pkb.pkt.getPacket();
        IP src = IP.from(arp.getSenderIp().toJavaArray());
        IP dst = IP.from(arp.getTargetIp().toJavaArray());
        buildAndSendPing(src, dst);
    }

    private void buildAndSendPing(IP src, IP dst) {
        assert Logger.lowLevelDebug("buildAndSendPing(" + src + ", " + dst + ")");

        IcmpPacket icmp = new IcmpPacket(src instanceof IPv6);
        icmp.setType(icmp.isIpv6() ? Consts.ICMPv6_PROTOCOL_TYPE_ECHO_REQ : Consts.ICMP_PROTOCOL_TYPE_ECHO_REQ);
        icmp.setCode(0);
        icmp.setOther(icmpPingOtherPart);

        AbstractIpPacket ipPkt = SwitchUtils.buildIpIcmpPacket(src, dst, icmp);
        sendPacket(ipPkt);
    }

    @Override
    public void destroy() {
        if (operateTun != null) {
            try {
                bondLoop.remove(operateTun);
            } catch (Throwable ignore) {
            }
            try {
                operateTun.close();
            } catch (IOException e) {
                Logger.shouldNotHappen("closing tun device failed", e);
            }
        }
    }

    @Override
    public int getLocalSideVni(int hint) {
        return localSideVni;
    }

    @Override
    public int getOverhead() {
        return 0;
    }

    private class TunHandler implements Handler<AbstractDatagramFD<?>> {
        private static final int TOTAL_LEN = SwitchUtils.TOTAL_RCV_BUF_LEN;
        private static final int PRESERVED_LEN = SwitchUtils.RCV_HEAD_PRESERVE_LEN;

        private final TunIface iface = TunIface.this;
        private final TapDatagramFD tunDatagramFD = TunIface.this.tun;

        private final ByteBuffer rcvBuf = Utils.allocateByteBuffer(TOTAL_LEN);
        private final ByteArray raw = ByteArray.from(rcvBuf.array());

        @Override
        public void accept(HandlerContext<AbstractDatagramFD<?>> ctx) {
            // will not fire
        }

        @Override
        public void connected(HandlerContext<AbstractDatagramFD<?>> ctx) {
            // will not fire
        }

        @Override
        public void readable(HandlerContext<AbstractDatagramFD<?>> ctx) {
            while (true) {
                VProxyThread.current().newUuidDebugInfo();

                int position = PRESERVED_LEN - (OS.isMac() ? 4 : 0);
                rcvBuf.limit(TOTAL_LEN).position(position);
                try {
                    ctx.getChannel().read(rcvBuf);
                } catch (IOException e) {
                    Logger.error(LogType.CONN_ERROR, "tun device " + tunDatagramFD + " got error when reading", e);
                    return;
                }
                if (rcvBuf.position() == position) {
                    break; // nothing read, quit loop
                }
                PacketBuffer pkb = PacketBuffer.fromIpBytes(iface, localSideVni, raw, PRESERVED_LEN, TOTAL_LEN - rcvBuf.position());
                String err = pkb.init();
                if (err != null) {
                    assert Logger.lowLevelDebug("got invalid packet: " + err);
                    continue;
                }

                received(pkb);
                callback.alertPacketsArrive();
            }
        }

        @Override
        public void writable(HandlerContext<AbstractDatagramFD<?>> ctx) {
            // ignore, and will not fire
        }

        @Override
        public void removed(HandlerContext<AbstractDatagramFD<?>> ctx) {
            Logger.warn(LogType.CONN_ERROR, "tun device " + tunDatagramFD + " removed from loop, it's not handled anymore, need to be closed");
            callback.alertDeviceDown(iface);
        }
    }
}
