package io.vproxy.vswitch.util;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Utils;
import io.vproxy.vfd.*;
import io.vproxy.vpacket.*;
import io.vproxy.vpacket.conntrack.tcp.TcpNat;
import io.vproxy.vpacket.conntrack.tcp.TcpState;
import io.vproxy.vpacket.conntrack.udp.UdpNat;
import io.vproxy.vswitch.IPMac;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.PacketFilterHelper;
import io.vproxy.vswitch.VirtualNetwork;
import io.vproxy.vswitch.iface.Iface;
import io.vproxy.vswitch.plugin.FilterResult;
import io.vproxy.vswitch.plugin.PacketFilter;
import io.vproxy.xdp.NativeXDP;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SwitchUtils {
    public static final int TOTAL_RCV_BUF_LEN = 4096;
    public static final int RCV_HEAD_PRESERVE_LEN = 128;
    public static final int RX_TX_CHUNKS = 2048;
    public static final MacAddress BROADCAST_MAC = new MacAddress("ff:ff:ff:ff:ff:ff");
    public static final MacAddress ZERO_MAC = new MacAddress("00:00:00:00:00:00");

    private SwitchUtils() {
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
        if (iface.getParams().getBaseMTU() < 0) {
            assert Logger.lowLevelDebug("iface " + iface.name() + " has mtu < 0, skip mss updating");
            return;
        }
        int maxMss = iface.getParams().getBaseMTU() - iface.getOverhead() - 20 /* tcp common */
            - 20 /* possible options in normal tcp packets, and also ip headers/opts */;
        if (!(pkb.pkt.getPacket() instanceof AbstractIpPacket)) {
            return; // only tcp requires modification
        }
        if (!(((AbstractIpPacket) pkb.pkt.getPacket()).getPacket() instanceof TcpPacket)) {
            return; // only tcp requires modification
        }
        AbstractIpPacket ip = (AbstractIpPacket) pkb.pkt.getPacket();
        TcpPacket tcp = (TcpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket();
        if ((tcp.getFlags() & Consts.TCP_FLAGS_SYN) != Consts.TCP_FLAGS_SYN) {
            return; // only handle syn and syn-ack
        }
        if (pkb.ensurePartialPacketParsed()) return;
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
                }
                break;
            }
        }
        if (foundMss) {
            return;
        }
        // need to add mss
        assert Logger.lowLevelDebug("add tcp mss: " + maxMss);
        TcpPacket.TcpOption opt = new TcpPacket.TcpOption(tcp);
        opt.setKind(Consts.TCP_OPTION_MSS);
        opt.setData(ByteArray.allocate(2).int16(0, maxMss));
        tcp.getOptions().add(opt);
        tcp.clearRawPacket();
        if (ip instanceof Ipv4Packet) {
            tcp.buildIPv4TcpPacket((Ipv4Packet) ip, AbstractPacket.FLAG_CHECKSUM_UNNECESSARY);
        } else {
            tcp.buildIPv6TcpPacket((Ipv6Packet) ip, AbstractPacket.FLAG_CHECKSUM_UNNECESSARY);
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
        Utils.execute(pb, 10 * 1000);
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
        resp.setSenderIp(srcIp.bytes);
        resp.setTargetMac(dst.bytes);
        resp.setTargetIp(dstIp.bytes);

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
            (ByteArray.allocate(4).set(0, (byte) 0b01100000 /*-R,+S,+O*/)).concat(requestedIpOrSrc.bytes)
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
        ipv6.setPayloadLength(icmp.getRawICMPv6Packet(ipv6, AbstractPacket.FLAG_CHECKSUM_UNNECESSARY).length());

        return ipv6;
    }

    public static Ipv6Packet buildNeighborSolicitationPacket(IPv6 targetIp, MacAddress senderMac, IPv6 senderIp) {
        IcmpPacket icmp = new IcmpPacket(true);
        icmp.setType(Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Solicitation);
        icmp.setCode(0);
        icmp.setOther(
            (ByteArray.allocate(4).set(0, (byte) 0)).concat(targetIp.bytes)
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
        ipv6.setPayloadLength(icmp.getRawICMPv6Packet(ipv6, AbstractPacket.FLAG_CHECKSUM_UNNECESSARY).length());

        return ipv6;
    }

    public static AbstractIpPacket buildIpIcmpPacket(IP srcIp, IP dstIp, IcmpPacket icmp) {
        AbstractIpPacket ipPkt;
        if (srcIp instanceof IPv4) {
            var ipv4 = new Ipv4Packet();
            ipv4.setVersion(4);
            ipv4.setIhl(5);
            ipv4.setTotalLength(20 + icmp.getRawPacket(AbstractPacket.FLAG_CHECKSUM_UNNECESSARY).length());
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
                (icmp.isIpv6()
                    ? icmp.getRawICMPv6Packet(ipv6, AbstractPacket.FLAG_CHECKSUM_UNNECESSARY)
                    : icmp.getRawPacket(AbstractPacket.FLAG_CHECKSUM_UNNECESSARY)
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

    public static AbstractIpPacket buildIpPacket(IP srcIp, IP dstIp, int proto, AbstractPacket pkt) {
        AbstractIpPacket ipPkt;
        if (srcIp instanceof IPv4) {
            var ipv4 = new Ipv4Packet();
            ipv4.setVersion(4);
            ipv4.setIhl(5);
            ipv4.setTotalLength(20 + pkt.getRawPacket(AbstractPacket.FLAG_CHECKSUM_UNNECESSARY).length());
            ipv4.setTtl(64);
            ipv4.setProtocol(proto);
            ipv4.setSrc((IPv4) srcIp);
            ipv4.setDst((IPv4) dstIp);
            ipv4.setOptions(ByteArray.allocate(0));
            ipv4.setPacket(pkt);
            ipPkt = ipv4;
        } else {
            assert srcIp instanceof IPv6;
            var ipv6 = new Ipv6Packet();
            ipv6.setVersion(6);
            ipv6.setNextHeader(proto);
            ipv6.setHopLimit(64);
            ipv6.setSrc((IPv6) srcIp);
            ipv6.setDst((IPv6) dstIp);
            ipv6.setExtHeaders(Collections.emptyList());
            ipv6.setPacket(pkt);
            ipv6.setPayloadLength(pkt.getRawPacket(AbstractPacket.FLAG_CHECKSUM_UNNECESSARY).length());
            ipPkt = ipv6;
        }
        return ipPkt;
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

    public static FilterResult applyFilters(ArrayList<PacketFilter> filters, PacketFilterHelper helper, PacketBuffer pkb) {
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < filters.size(); i++) {
            PacketFilter filter = filters.get(i);
            var res = filter.handle(helper, pkb);
            if (res != FilterResult.PASS) {
                return res;
            }
        }
        return FilterResult.PASS;
    }

    public static int checksumFlagsFor(EthernetPacket pkt) {
        assert Logger.lowLevelDebug("checksumFlagsFor(" + pkt + ")");
        if (!(pkt.getPacket() instanceof AbstractIpPacket)) {
            return 0;
        }
        var ip = (AbstractIpPacket) pkt.getPacket();
        int ret = 0;
        if (ip.isRequireUpdatingChecksum()) {
            ret |= NativeXDP.VP_CSUM_IP;
        }
        if (ip.getPacket().isRequireUpdatingChecksum()) {
            ret |= NativeXDP.VP_CSUM_UP;
        }
        assert Logger.lowLevelDebug("checksumFlagsFor(" + pkt + ") return " + ret);
        return ret;
    }

    public static boolean executeTcpNat(PacketBuffer pkb, TcpNat nat) {
        assert Logger.lowLevelDebug("executeTcpNat(" + pkb + ", " + nat + ")");
        var pkt = pkb.tcpPkt;
        boolean isBackhaul = pkb.ipPkt.getSrc().equals(nat._2.remote.getAddress()) &&
            pkb.tcpPkt.getSrcPort() == nat._2.remote.getPort();
        assert Logger.lowLevelDebug("isBackhaul = " + isBackhaul);

        if (pkt.isRst()) {
            assert Logger.lowLevelDebug("the packet is RST");
            nat.setState(TcpState.CLOSED);
        } else {
            assert Logger.lowLevelDebug("the packet is not RST");
            var state = nat.getState();
            switch (state) {
                case TIME_WAIT:
                case CLOSED:
                    if (!pkt.isSyn() || pkt.isAck()) {
                        assert Logger.lowLevelDebug("is not syn, not a new session");
                        nat.resetTimer();
                    } else {
                        if (nat.proxyProtocolHelper != null) {
                            buildSynPacketForProxyProtocol(pkb, nat);
                        }
                        nat.setState(TcpState.SYN_SENT);
                    }
                    break;
                case SYN_SENT:
                    boolean handled = false;
                    if (isBackhaul) {
                        if (nat.proxyProtocolHelper != null) {
                            if (pkt.isSyn() && pkt.isAck()) {
                                nat.resetTimer();
                                return handleProxyProtocolHeader(pkb, nat);
                            }
                            if (pkt.isAck()) { // ack, no syn
                                if (nat.proxyProtocolHelper.isSent) {
                                    // the proxy protocol header is acked
                                    // need to send the responding packet
                                    buildSynAckPacketForProxyProtocol(pkb);
                                    nat.setState(TcpState.SYN_RECEIVED);
                                    handled = true;
                                } else {
                                    assert Logger.lowLevelDebug("received unexpected packet, " +
                                        "the proxy protocol header is not sent yet, but received !syn + ack packet from the passive side");
                                    return false; // drop
                                }
                            }
                        } else {
                            if (pkt.isSyn() && pkt.isAck()) {
                                nat.setState(TcpState.SYN_RECEIVED);
                                handled = true;
                            }
                        }
                    } else {
                        if (nat.proxyProtocolHelper != null) {
                            if (pkt.isSyn() && !pkt.isAck()) {
                                // is syn retransmission, allow it to pass
                                buildSynPacketForProxyProtocol(pkb, nat);
                            } else {
                                // no other packet is allowed from the active side when handling proxy protocol
                                return false; // drop
                            }
                        }
                    }
                    if (!handled) {
                        nat.resetTimer();
                    }
                    break;
                case SYN_RECEIVED:
                    if (!isBackhaul && !pkt.isSyn() && pkt.isAck()) {
                        nat.proxyProtocolHelper = null;
                        nat.setState(TcpState.ESTABLISHED);
                    } else {
                        if (isBackhaul && nat.proxyProtocolHelper != null) {
                            buildSynAckPacketForProxyProtocol(pkb);
                        }
                        nat.resetTimer();
                    }
                    break;
                case ESTABLISHED:
                    if (pkt.isFin()) {
                        if (isBackhaul) {
                            nat.setState(TcpState.CLOSE_WAIT);
                        } else {
                            nat.setState(TcpState.FIN_WAIT_1);
                        }
                    } else {
                        nat.resetTimer();
                    }
                    break;
                case FIN_WAIT_1:
                    if (isBackhaul && pkt.isAck()) {
                        if (pkt.isFin()) {
                            nat.setState(TcpState.TIME_WAIT);
                        } else {
                            nat.setState(TcpState.FIN_WAIT_2);
                        }
                    } else {
                        nat.resetTimer();
                    }
                    break;
                case FIN_WAIT_2:
                    if (isBackhaul && pkt.isFin()) {
                        nat.setState(TcpState.TIME_WAIT);
                    } else {
                        nat.resetTimer();
                    }
                    break;
                case CLOSE_WAIT:
                    if (!isBackhaul && pkt.isFin()) {
                        if (pkt.isAck()) {
                            nat.setState(TcpState.TIME_WAIT);
                        } else {
                            nat.setState(TcpState.CLOSING);
                        }
                    } else {
                        nat.resetTimer();
                    }
                    break;
                case CLOSING:
                    if (pkt.isAck()) {
                        nat.setState(TcpState.TIME_WAIT);
                    } else {
                        nat.resetTimer();
                    }
                    break;
                default:
                    Logger.shouldNotHappen("should not reach here: " + state);
            }
        }

        applyNat(isBackhaul, nat._1.remote, nat._1.local, nat._2.remote, nat._2.local, pkb, pkt);

        if (isBackhaul) {
            pkb.tcp = nat._1;
        } else {
            pkb.tcp = nat._2;
        }
        pkb.fastpath = true;

        return true;
    }

    private static void buildSynPacketForProxyProtocol(PacketBuffer pkb, TcpNat nat) {
        var pkt = pkb.tcpPkt;
        pkt.initPartial(PartialPacket.LEVEL_HANDLED_FIELDS);
        pkt.setSeqNum(pkt.getSeqNum() - nat.proxyProtocolHelper.getV2HeaderLength());
    }

    private static void buildSynAckPacketForProxyProtocol(PacketBuffer pkb) {
        var pkt = pkb.tcpPkt;
        pkt.initPartial(PartialPacket.LEVEL_HANDLED_FIELDS);
        pkt.setSeqNum(pkt.getSeqNum() - 1);
        pkt.setAckNum(pkt.getAckNum());
        pkt.setFlags(Consts.TCP_FLAGS_SYN | Consts.TCP_FLAGS_ACK);
    }

    private static boolean handleProxyProtocolHeader(PacketBuffer pkb, TcpNat nat) {
        if (pkb.ensurePartialPacketParsed()) {
            assert Logger.lowLevelDebug("partial packet parsing failed");
            return false;
        }

        pkb.ipPkt.swapSrcDst();
        var port = pkb.tcpPkt.getSrcPort();
        pkb.tcpPkt.setSrcPort(pkb.tcpPkt.getDstPort());
        pkb.tcpPkt.setDstPort(port);
        var seq = pkb.tcpPkt.getSeqNum();
        pkb.tcpPkt.setSeqNum(pkb.tcpPkt.getAckNum());
        pkb.tcpPkt.setAckNum(seq + 1);
        pkb.tcpPkt.setFlags(Consts.TCP_FLAGS_PSH | Consts.TCP_FLAGS_ACK);
        pkb.tcpPkt.setOptions(List.of());

        var hdr = nat.proxyProtocolHelper.buildV2Header();
        pkb.tcpPkt.setData(hdr);

        pkb.ipPkt.setHopLimit(255);

        nat.proxyProtocolHelper.isSent = true;
        pkb.tcp = nat._2;
        pkb.fastpath = true;

        return true;
    }

    public static void executeUdpNat(PacketBuffer pkb, UdpNat nat) {
        assert Logger.lowLevelDebug("executeUdpNat(" + pkb + ", " + nat + ")");
        var pkt = pkb.udpPkt;
        boolean isBackhaul = pkb.ipPkt.getSrc().equals(nat._2.remote.getAddress()) &&
            pkb.udpPkt.getSrcPort() == nat._2.remote.getPort();
        assert Logger.lowLevelDebug("isBackhaul = " + isBackhaul);

        nat.resetTimer();

        applyNat(isBackhaul, nat._1.remote, nat._1.local, nat._2.remote, nat._2.local, pkb, pkt);

        if (isBackhaul) {
            pkb.udp = nat._1;
        } else {
            pkb.udp = nat._2;
        }
        pkb.fastpath = true;
    }

    private static void applyNat(boolean isBackhaul, IPPort _1remote, IPPort _1local, IPPort _2remote, IPPort _2local,
                                 PacketBuffer pkb, TransportPacket pkt) {
        if (isBackhaul) {
            applyNat(_1remote, _1local, pkb, pkt, _1local.getAddress() instanceof IPv4);
        } else {
            applyNat(_2remote, _2local, pkb, pkt, _1local.getAddress() instanceof IPv4);
        }
    }

    private static void applyNat(IPPort remote, IPPort local, PacketBuffer pkb, TransportPacket pkt, boolean ipv4) {
        assert Logger.lowLevelDebug("change pkt to " + local + " -> " + remote);
        if (ipv4) {
            ((Ipv4Packet) pkb.ipPkt).setSrc((IPv4) local.getAddress());
            ((Ipv4Packet) pkb.ipPkt).setDst((IPv4) remote.getAddress());
        } else {
            ((Ipv6Packet) pkb.ipPkt).setSrc((IPv6) local.getAddress());
            ((Ipv6Packet) pkb.ipPkt).setDst((IPv6) remote.getAddress());
        }
        pkt.setSrcPort(local.getPort());
        pkt.setDstPort(remote.getPort());
    }

    public static IPMac getRoutedSrcIpAndMac(VirtualNetwork network, IP dstIp) {
        assert Logger.lowLevelDebug("getRoutedSrcIpAndMac(" + network + "," + dstIp + ")");

        // find an ip in that network to be used for the src mac address
        if (dstIp instanceof IPv4) {
            return network.ips.findAnyIPv4ForRouting();
        } else {
            return network.ips.findAnyIPv6ForRouting();
        }
    }

    public static MacAddress getRoutedSrcMac(VirtualNetwork network, IP dstIp) {
        assert Logger.lowLevelDebug("getRoutedSrcMac(" + network + "," + dstIp + ")");
        var entry = getRoutedSrcIpAndMac(network, dstIp);
        if (entry == null) {
            return null;
        }
        return entry.mac;
    }

    public static EthernetPacket buildArpReq(VirtualNetwork network, IP dstIp, MacAddress dstMac) {
        assert Logger.lowLevelDebug("buildArpReq(" + network + "," + dstIp + "," + dstMac + ")");

        var optIp = network.ips.findAnyIPv4ForRouting();
        if (optIp == null) {
            assert Logger.lowLevelDebug("cannot find synthetic ipv4 in the network");
            return null;
        }
        IP reqIp = optIp.ip;
        MacAddress reqMac = optIp.mac;

        ArpPacket req = new ArpPacket();
        req.setHardwareType(Consts.ARP_HARDWARE_TYPE_ETHER);
        req.setProtocolType(Consts.ARP_PROTOCOL_TYPE_IP);
        req.setHardwareSize(6);
        req.setProtocolSize(4);
        req.setOpcode(Consts.ARP_PROTOCOL_OPCODE_REQ);
        req.setSenderMac(reqMac.bytes);
        req.setSenderIp(reqIp.bytes);
        req.setTargetMac(ByteArray.allocate(6));
        req.setTargetIp(dstIp.bytes);

        EthernetPacket ether = new EthernetPacket();
        ether.setDst(dstMac);
        ether.setSrc(reqMac);
        ether.setType(Consts.ETHER_TYPE_ARP);
        ether.setPacket(req);

        return ether;
    }

    public static EthernetPacket buildNdpNeighborSolicitation(VirtualNetwork network, IP dstIp, MacAddress dstMac) {
        assert Logger.lowLevelDebug("buildNdpNeighborSolicitation(" + network + "," + dstIp + "," + dstMac + ")");

        var optIp = network.ips.findAnyIPv6ForRouting();
        if (optIp == null) {
            assert Logger.lowLevelDebug("cannot find synthetic ipv4 in the network");
            return null;
        }
        IP reqIp = optIp.ip;
        MacAddress reqMac = optIp.mac;

        Ipv6Packet ipv6 = SwitchUtils.buildNeighborSolicitationPacket((IPv6) dstIp, reqMac, (IPv6) reqIp);

        EthernetPacket ether = new EthernetPacket();
        ether.setDst(dstMac);
        ether.setSrc(reqMac);
        ether.setType(Consts.ETHER_TYPE_IPv6);
        ether.setPacket(ipv6);

        return ether;
    }
}
