package vproxy.vswitch.util;

import vproxy.base.util.ByteArray;
import vproxy.base.util.Consts;
import vproxy.base.util.Logger;
import vproxy.base.util.Utils;
import vproxy.vfd.IP;
import vproxy.vfd.IPv4;
import vproxy.vfd.IPv6;
import vproxy.vfd.MacAddress;
import vproxy.vpacket.*;
import vproxy.vswitch.PacketBuffer;
import vproxy.vswitch.iface.Iface;
import vproxy.vswitch.iface.LocalSideVniGetterSetter;
import vproxy.vswitch.iface.RemoteSideVniGetterSetter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SwitchUtils {
    public static final int TOTAL_RCV_BUF_LEN = 4096;
    public static final int RCV_HEAD_PRESERVE_LEN = 512;
    public static final int RX_TX_CHUNKS = 2048;
    public static final MacAddress BROADCAST_MAC = new MacAddress("ff:ff:ff:ff:ff:ff");
    public static final MacAddress ZERO_MAC = new MacAddress("00:00:00:00:00:00");

    private SwitchUtils() {
    }

    public static void updateBothSideVni(Iface iface, Iface newIface) {
        assert iface.equals(newIface);
        if (iface instanceof RemoteSideVniGetterSetter) {
            var that = (RemoteSideVniGetterSetter) newIface;
            if (that.getRemoteSideVni() != 0) {
                ((RemoteSideVniGetterSetter) iface).setRemoteSideVni(that.getRemoteSideVni());
            }
        }
        if (iface instanceof LocalSideVniGetterSetter) {
            var that = (LocalSideVniGetterSetter) newIface;
            var self = (LocalSideVniGetterSetter) iface;
            var newVal = that.getLocalSideVni(0);
            if (newVal != 0) {
                self.setLocalSideVni(newVal);
            }
        }
    }

    public static VXLanPacket getOrMakeVXLanPacket(PacketBuffer pkb) {
        if (pkb.vxlan == null) {
            var p = new VXLanPacket();
            p.setVni(pkb.vni);
            p.setPacket(pkb.pkt);
            pkb.vxlan = p;
        }
        return pkb.vxlan;
    }

    public static void checkAndUpdateMss(PacketBuffer pkb, Iface iface) {
        int maxMss = iface.getBaseMTU() - iface.getOverhead() - 20 /* tcp common */
            - 20 /* possible options in normal tcp packets, and also ip headers/opts */;
        if (!(pkb.pkt.getPacket() instanceof AbstractIpPacket) ||
            !(((AbstractIpPacket) pkb.pkt.getPacket()).getPacket() instanceof TcpPacket)) {
            return; // only tcp requires modification
        }
        AbstractIpPacket ip = (AbstractIpPacket) pkb.pkt.getPacket();
        TcpPacket tcp = (TcpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket();
        if ((tcp.getFlags() & Consts.TCP_FLAGS_SYN) != Consts.TCP_FLAGS_SYN) {
            return; // only handle syn and syn-ack
        }
        if (tcp.getOptions() == null) {
            tcp.setOptions(new ArrayList<>(1));
        }
        boolean foundMss = false;
        for (TcpPacket.TcpOption opt : tcp.getOptions()) {
            if (opt.getKind() == Consts.TCP_OPTION_MSS) {
                foundMss = true;
                int originalMss = opt.getData().uint16(0);
                if (originalMss > maxMss) {
                    assert Logger.lowLevelDebug("tcp mss updated from " + originalMss + " to " + maxMss);
                    opt.setData(ByteArray.allocate(2).int16(0, maxMss));
                    tcp.clearRawPacket();
                    if (ip instanceof Ipv4Packet) {
                        tcp.buildIPv4TcpPacket((Ipv4Packet) ip);
                    } else {
                        tcp.buildIPv6TcpPacket((Ipv6Packet) ip);
                    }
                }
                break;
            }
        }
        if (foundMss) {
            return;
        }
        // need to add mss
        assert Logger.lowLevelDebug("add tcp mss: " + maxMss);
        TcpPacket.TcpOption opt = new TcpPacket.TcpOption();
        opt.setKind(Consts.TCP_OPTION_MSS);
        opt.setData(ByteArray.allocate(2).int16(0, maxMss));
        tcp.getOptions().add(opt);
        tcp.clearRawPacket();
        if (ip instanceof Ipv4Packet) {
            tcp.buildIPv4TcpPacket((Ipv4Packet) ip);
        } else {
            tcp.buildIPv6TcpPacket((Ipv6Packet) ip);
        }
    }

    public static void executeDevPostScript(String switchAlias, String dev, int vni, String postScript) throws Exception {
        if (postScript == null || postScript.isBlank()) {
            return;
        }
        ProcessBuilder pb = new ProcessBuilder().command(postScript);
        var env = pb.environment();
        env.put("DEV", dev);
        env.put("VNI", "" + vni);
        env.put("SWITCH", switchAlias);
        Process p = pb.start();
        Utils.pipeOutputOfSubProcess(p);
        p.waitFor(10, TimeUnit.SECONDS);
        if (p.isAlive()) {
            p.destroyForcibly();
            throw new Exception("the process took too long to execute");
        }
        int exit = p.exitValue();
        if (exit == 0) {
            return;
        }
        throw new Exception("exit code is " + exit);
    }

    public static IPv6 extractTargetAddressFromNeighborSolicitation(IcmpPacket inIcmp) {
        ByteArray other = inIcmp.getOther();
        if (other.length() < 20) { // 4 reserved and 16 target address
            assert Logger.lowLevelDebug("invalid packet for neighbor solicitation: too short");
            return null;
        }
        assert Logger.lowLevelDebug("is a valid neighbor solicitation");

        byte[] targetAddr = other.sub(4, 16).toJavaArray();
        return IP.fromIPv6(targetAddr);
    }

    public static ArpPacket buildArpPacket(int opcode, MacAddress dst, IPv4 dstIp, MacAddress src, IPv4 srcIp) {
        ArpPacket resp = new ArpPacket();
        resp.setHardwareType(Consts.ARP_HARDWARE_TYPE_ETHER);
        resp.setProtocolType(Consts.ARP_PROTOCOL_TYPE_IP);
        resp.setHardwareSize(6);
        resp.setProtocolSize(4);
        resp.setOpcode(opcode);
        resp.setSenderMac(src.bytes);
        resp.setSenderIp(ByteArray.from(srcIp.getAddress()));
        resp.setTargetMac(dst.bytes);
        resp.setTargetIp(ByteArray.from(dstIp.getAddress()));

        return resp;
    }

    public static EthernetPacket buildEtherArpPacket(MacAddress dst, MacAddress src, ArpPacket arp) {
        EthernetPacket ether = new EthernetPacket();
        ether.setDst(dst);
        ether.setSrc(src);
        ether.setType(Consts.ETHER_TYPE_ARP);
        ether.setPacket(arp);
        return ether;
    }

    public static Ipv6Packet buildNeighborAdvertisementPacket(MacAddress requestedMac, IPv6 requestedIpOrSrc, IPv6 dstIp) {
        IcmpPacket icmp = new IcmpPacket(true);
        icmp.setType(Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Advertisement);
        icmp.setCode(0);
        icmp.setOther(
            (ByteArray.allocate(4).set(0, (byte) 0b01100000 /*-R,+S,+O*/)).concat(ByteArray.from(requestedIpOrSrc.getAddress()))
                .concat(( // the target link-layer address
                    ByteArray.allocate(1 + 1).set(0, (byte) Consts.ICMPv6_OPTION_TYPE_Target_Link_Layer_Address)
                        .set(1, (byte) 1) // mac address len = 6, (1 + 1 + 6)/8 = 1
                        .concat(requestedMac.bytes)
                ))
        );

        Ipv6Packet ipv6 = new Ipv6Packet();
        ipv6.setVersion(6);
        ipv6.setNextHeader(Consts.IP_PROTOCOL_ICMPv6);
        ipv6.setHopLimit(255);
        ipv6.setSrc(requestedIpOrSrc);
        ipv6.setDst(dstIp);
        ipv6.setExtHeaders(Collections.emptyList());
        ipv6.setPacket(icmp);
        ipv6.setPayloadLength(icmp.getRawICMPv6Packet(ipv6).length());

        return ipv6;
    }

    public static Ipv6Packet buildNeighborSolicitationPacket(IPv6 targetIp, MacAddress senderMac, IPv6 senderIp) {
        IcmpPacket icmp = new IcmpPacket(true);
        icmp.setType(Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Solicitation);
        icmp.setCode(0);
        icmp.setOther(
            (ByteArray.allocate(4).set(0, (byte) 0)).concat(ByteArray.from(targetIp.getAddress()))
                .concat(( // the source link-layer address
                    ByteArray.allocate(1 + 1).set(0, (byte) Consts.ICMPv6_OPTION_TYPE_Source_Link_Layer_Address)
                        .set(1, (byte) 1) // mac address len = 6, (1 + 1 + 6)/8 = 1
                        .concat(senderMac.bytes)
                ))
        );

        Ipv6Packet ipv6 = new Ipv6Packet();
        ipv6.setVersion(6);
        ipv6.setNextHeader(Consts.IP_PROTOCOL_ICMPv6);
        ipv6.setHopLimit(255);
        ipv6.setSrc(senderIp);
        byte[] foo = Consts.IPv6_Solicitation_Node_Multicast_Address.toNewJavaArray();
        byte[] bar = targetIp.getAddress();
        foo[13] = bar[13];
        foo[14] = bar[14];
        foo[15] = bar[15];
        ipv6.setDst(IP.fromIPv6(foo));
        ipv6.setExtHeaders(Collections.emptyList());
        ipv6.setPacket(icmp);
        ipv6.setPayloadLength(icmp.getRawICMPv6Packet(ipv6).length());

        return ipv6;
    }

    public static AbstractIpPacket buildIpIcmpPacket(IP srcIp, IP dstIp, IcmpPacket icmp) {
        AbstractIpPacket ipPkt;
        if (srcIp instanceof IPv4) {
            var ipv4 = new Ipv4Packet();
            ipv4.setVersion(4);
            ipv4.setIhl(5);
            ipv4.setTotalLength(20 + icmp.getRawPacket().length());
            ipv4.setTtl(64);
            ipv4.setProtocol(Consts.IP_PROTOCOL_ICMP);
            ipv4.setSrc((IPv4) srcIp);
            ipv4.setDst((IPv4) dstIp);
            ipv4.setOptions(ByteArray.allocate(0));
            ipv4.setPacket(icmp);
            ipPkt = ipv4;
        } else {
            assert srcIp instanceof IPv6;
            var ipv6 = new Ipv6Packet();
            ipv6.setVersion(6);
            ipv6.setNextHeader(icmp.isIpv6() ? Consts.IP_PROTOCOL_ICMPv6 : Consts.IP_PROTOCOL_ICMP);
            ipv6.setHopLimit(64);
            ipv6.setSrc((IPv6) srcIp);
            ipv6.setDst((IPv6) dstIp);
            ipv6.setExtHeaders(Collections.emptyList());
            ipv6.setPacket(icmp);
            ipv6.setPayloadLength(
                (
                    icmp.isIpv6() ? icmp.getRawICMPv6Packet(ipv6) : icmp.getRawPacket()
                ).length()
            );
            ipPkt = ipv6;
        }
        return ipPkt;
    }

    public static EthernetPacket buildEtherIpIcmpPacket(MacAddress dstMac, MacAddress srcMac, IP srcIp, IP dstIp, IcmpPacket icmp) {
        assert Logger.lowLevelDebug("buildIpIcmpPacket(" + dstMac + "," + srcMac + "," + srcIp + "," + dstIp + "," + icmp + ")");
        AbstractIpPacket ipPkt = buildIpIcmpPacket(srcIp, dstIp, icmp);
        return buildEtherIpPacket(dstMac, srcMac, ipPkt);
    }

    public static EthernetPacket buildEtherIpPacket(MacAddress dstMac, MacAddress srcMac, AbstractIpPacket ipPkt) {
        var srcIp = ipPkt.getSrc();

        EthernetPacket ether = new EthernetPacket();
        ether.setDst(dstMac);
        ether.setSrc(srcMac);
        ether.setType(srcIp instanceof IPv4 ? Consts.ETHER_TYPE_IPv4 : Consts.ETHER_TYPE_IPv6);
        ether.setPacket(ipPkt);

        return ether;
    }
}
