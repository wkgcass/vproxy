package vproxy.vswitch.iface;

import vproxy.base.selector.Handler;
import vproxy.base.selector.HandlerContext;
import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.selector.wrap.blocking.BlockingDatagramFD;
import vproxy.base.util.*;
import vproxy.base.util.exception.XException;
import vproxy.base.util.thread.VProxyThread;
import vproxy.vfd.*;
import vproxy.vpacket.*;
import vproxy.vswitch.PacketBuffer;
import vproxy.vswitch.util.SwitchUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class TunIface extends Iface {
    private final ByteArray icmpPingPayloadPrefix = ByteArray.from("vpsstunarp");
    private final ByteArray icmpPingPayloadPlaceHolder = ByteArray.from("macadd"); // 6 bytes mac of the original req pkt
    private final ByteArray icmpPingOtherPart = ByteArray.from("id").concat(ByteArray.from(0, 0))
        .concat(icmpPingPayloadPrefix)
        .concat(icmpPingPayloadPlaceHolder)
        .arrange();

    public final String dev;
    private TapDatagramFD tun;
    public final int localSideVni;
    public final MacAddress mac;
    public final String postScript;

    private AbstractDatagramFD<?> operateTun;
    private SelectorEventLoop bondLoop;

    private final ByteBuffer sndBuf = ByteBuffer.allocateDirect(2048);

    public TunIface(String dev,
                    int localSideVni,
                    MacAddress mac,
                    String postScript) {
        this.dev = dev;
        this.localSideVni = localSideVni;
        this.mac = mac;
        this.postScript = postScript;
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
    public String name() {
        return "tun:" + tun.getTap().dev;
    }

    @Override
    protected String toStringExtra() {
        return ",vni:" + localSideVni + ",mac:" + mac;
    }

    @Override
    public void init(IfaceInitParams params) throws Exception {
        super.init(params);

        bondLoop = params.loop;
        FDs fds = FDProvider.get().getProvided();
        FDsWithTap tapFDs = (FDsWithTap) fds;
        tun = tapFDs.openTun(dev);
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
        if (handleArpOrNdpOutput(pkb)) {
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
        var bytes = ipPkt.getRawPacket(0).toJavaArray();
        if (OS.isMac()) { // add AF header for macos
            if (ipPkt instanceof Ipv4Packet) {
                sndBuf.putInt(Consts.AF_INET);
            } else {
                sndBuf.putInt(Consts.AF_INET6);
            }
        }

        statistics.incrTxPkts();
        statistics.incrTxBytes(bytes.length);

        sndBuf.put(bytes);
        sndBuf.flip();
        try {
            operateTun.write(sndBuf);
        } catch (IOException e) {
            Logger.error(LogType.SOCKET_ERROR, "sending packet to " + this + " failed", e);
            statistics.incrTxErr();
        }
    }

    private boolean handleArpOrNdpOutput(PacketBuffer pkb) {
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
            transformToPingOutput(src, dst, pkb.pkt.getSrc());
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
            handleArpOutput(pkb);
        }
        assert Logger.lowLevelDebug("not arp nor neighbor solicitation");
        return false;
    }

    private void handleArpOutput(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("handling arp for tun dev");
        var arp = (ArpPacket) pkb.pkt.getPacket();
        IP src = IP.from(arp.getSenderIp().toJavaArray());
        IP dst = IP.from(arp.getTargetIp().toJavaArray());
        transformToPingOutput(src, dst, pkb.pkt.getSrc());
    }

    private void transformToPingOutput(IP src, IP dst, MacAddress pktSrc) {
        assert Logger.lowLevelDebug("transformToPingOutput(" + src + ", " + dst + ")");

        IcmpPacket icmp = new IcmpPacket(src instanceof IPv6);
        icmp.setType(icmp.isIpv6() ? Consts.ICMPv6_PROTOCOL_TYPE_ECHO_REQ : Consts.ICMP_PROTOCOL_TYPE_ECHO_REQ);
        icmp.setCode(0);
        int id = (int) (Math.random() * 256);
        icmpPingOtherPart.int16(0, id);
        for (int i = 0; i < 6; ++i) {
            icmpPingOtherPart.set(4 + icmpPingPayloadPrefix.length() + i, pktSrc.bytes.get(i));
        }
        icmp.setOther(icmpPingOtherPart);

        AbstractIpPacket ipPkt = SwitchUtils.buildIpIcmpPacket(src, dst, icmp);
        sendPacket(ipPkt);
    }

    // need to convert pong to arp/ndp responses
    private void transformToArpOrNdpInput(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("transformToArpOrNdpInput(" + pkb + ")");

        if (!(pkb.ipPkt.getPacket() instanceof IcmpPacket)) {
            assert Logger.lowLevelDebug("not icmp packet");
            return;
        }
        var icmp = (IcmpPacket) pkb.ipPkt.getPacket();
        if (icmp.isIpv6()) {
            if (icmp.getType() != Consts.ICMPv6_PROTOCOL_TYPE_ECHO_RESP) {
                assert Logger.lowLevelDebug("not icmp resp (v6)");
                return;
            }
        } else {
            if (icmp.getType() != Consts.ICMP_PROTOCOL_TYPE_ECHO_RESP) {
                assert Logger.lowLevelDebug("not icmp resp");
                return;
            }
        }
        ByteArray other = icmp.getOther();
        if (other.length() != icmpPingOtherPart.length()) {
            assert Logger.lowLevelDebug("icmp length mismatch");
            return;
        }
        if (!other.sub(4, icmpPingPayloadPrefix.length()).equals(icmpPingPayloadPrefix)) {
            assert Logger.lowLevelDebug("pong prefix mismatch");
            return;
        }
        MacAddress dst = new MacAddress(other.sub(4 + icmpPingPayloadPrefix.length(), 6));

        EthernetPacket ether;
        if (pkb.ipPkt instanceof Ipv4Packet) {
            ether = transformToArpInput((IPv4) pkb.ipPkt.getSrc(), (IPv4) pkb.ipPkt.getDst(), dst);
        } else {
            ether = transformToNdpInput((IPv6) pkb.ipPkt.getSrc(), (IPv6) pkb.ipPkt.getDst(), dst);
        }

        pkb.replacePacket(ether);
        assert Logger.lowLevelDebug("packet transformed: " + pkb);
    }

    private EthernetPacket transformToArpInput(IPv4 src, IPv4 dst, MacAddress dldst) {
        var arp = SwitchUtils.buildArpPacket(Consts.ARP_PROTOCOL_OPCODE_RESP, dldst, dst, mac, src);
        return SwitchUtils.buildEtherArpPacket(dldst, mac, arp);
    }

    private EthernetPacket transformToNdpInput(IPv6 src, IPv6 dst, MacAddress dldst) {
        var ipicmp = SwitchUtils.buildNeighborAdvertisementPacket(mac, src, dst);
        return SwitchUtils.buildEtherIpPacket(dldst, mac, ipicmp);
    }

    @Override
    public void destroy() {
        if (isDestroyed()) {
            return;
        }
        super.destroy();
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

                statistics.incrRxPkts();
                statistics.incrRxBytes(pkb.pktBuf.length());

                transformToArpOrNdpInput(pkb);

                received(pkb);
                callback.alertPacketsArrive(TunIface.this);
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
