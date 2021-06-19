package vproxy.vswitch.stack;

import vproxy.base.util.ByteArray;
import vproxy.base.util.Consts;
import vproxy.base.util.Logger;
import vproxy.vfd.IP;
import vproxy.vfd.IPv4;
import vproxy.vfd.IPv6;
import vproxy.vfd.MacAddress;
import vproxy.vpacket.*;
import vproxy.vswitch.IPMac;
import vproxy.vswitch.SocketBuffer;
import vproxy.vswitch.SwitchContext;
import vproxy.vswitch.Table;

import java.util.Collections;

public class L3 {
    private final SwitchContext swCtx;
    private final L2 L2;
    public final L4 L4;

    public L3(SwitchContext swCtx, L2 l2) {
        this.swCtx = swCtx;
        L2 = l2;
        L4 = new L4(swCtx, this);
    }

    public void input(SocketBuffer skb) {
        assert Logger.lowLevelDebug("L3.input(" + skb + ")");
        if (skb.pkt.getDst().isUnicast()) {
            handleInputUnicast(skb);
        } else {
            handleInputBroadcast(skb);
        }
    }

    public void resolve(Table table, IP ip, MacAddress knownMac) {
        assert Logger.lowLevelDebug("lookupAddress(" + table + "," + ip + "," + knownMac + ")");
        if (ip instanceof IPv4) {
            arpResolve(table, ip, knownMac);
        } else {
            ndpResolve(table, ip, knownMac);
        }
    }

    private void arpResolve(Table table, IP ip, MacAddress knownMac) {
        assert Logger.lowLevelDebug("lookupAddress(" + table + "," + ip + "," + knownMac + ")");
        var iface = knownMac == null ? null : table.macTable.lookup(knownMac);
        if (iface == null) {
            assert Logger.lowLevelDebug(" cannot find iface of the mac, try broadcast");
            broadcastArp(table, ip);
        } else {
            assert Logger.lowLevelDebug(" run unicast");
            unicastArp(table, ip, knownMac);
        }
    }

    private void ndpResolve(Table table, IP ip, MacAddress knownMac) {
        assert Logger.lowLevelDebug("lookupAddress(" + table + "," + ip + "," + knownMac + ")");
        var iface = table.macTable.lookup(knownMac);
        if (iface == null) {
            assert Logger.lowLevelDebug("cannot find iface of the mac, try broadcast");
            broadcastNdp(table, ip);
        } else {
            assert Logger.lowLevelDebug("run unicast");
            unicastNdp(table, ip, knownMac);
        }
    }

    private void handleInputUnicast(SocketBuffer skb) {
        assert Logger.lowLevelDebug("handleInputUnicast(" + skb + ")");

        var packet = skb.pkt.getPacket();
        if (packet instanceof ArpPacket) {
            handleArp(skb);
            return;
        } else if (packet instanceof AbstractIpPacket) {
            handleUnicastIP(skb);
            return;
        }
        assert Logger.lowLevelDebug("cannot process the packet");
    }

    private void handleInputBroadcast(SocketBuffer skb) {
        assert Logger.lowLevelDebug("handleInputBroadcast(" + skb + ")");

        var packet = skb.pkt.getPacket();
        if (packet instanceof ArpPacket) {
            handleArp(skb);
            return;
        } else if (packet instanceof AbstractIpPacket) {
            assert Logger.lowLevelDebug("is ip packet");
            // we only handle icmpv6 packets when it's broadcasted

            var ipPkt = (AbstractIpPacket) packet;

            if (!(ipPkt instanceof Ipv6Packet)) {
                assert Logger.lowLevelDebug("is not ipv6. the packet protocol is " + ipPkt.getProtocol());
                return;
            }
            if (!(ipPkt.getPacket() instanceof IcmpPacket)) {
                assert Logger.lowLevelDebug("is not icmp");
                return;
            }
            var icmpPkt = (IcmpPacket) ipPkt.getPacket();
            if (!icmpPkt.isIpv6()) {
                assert Logger.lowLevelDebug("is not icmpv6");
                return;
            }
            if (icmpPkt.getType() != Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Solicitation) {
                assert Logger.lowLevelDebug("is not neighbor solicitation");
                return;
            }
            handleNeighborSolicitation(skb);
            return;
        }
        assert Logger.lowLevelDebug("cannot process the packet");
    }

    private void handleArp(SocketBuffer skb) {
        assert Logger.lowLevelDebug("handleArp(" + skb + ")");

        ArpPacket arp = (ArpPacket) skb.pkt.getPacket();
        if (arp.getProtocolType() != Consts.ARP_PROTOCOL_TYPE_IP) {
            assert Logger.lowLevelDebug("type of arp packet is not ip");
            return;
        }
        assert Logger.lowLevelDebug("arp protocol is ip");
        if (arp.getOpcode() != Consts.ARP_PROTOCOL_OPCODE_REQ) {
            assert Logger.lowLevelDebug("cannot handle this type arp message");
            return;
        }
        assert Logger.lowLevelDebug("arp is req");

        // only handle ipv4 in arp, v6 should be handled with ndp
        ByteArray targetIp = arp.getTargetIp();
        if (targetIp.length() != 4) {
            assert Logger.lowLevelDebug("target ip length is not 4");
            return;
        }
        assert Logger.lowLevelDebug("arp target is ipv4");
        IP ip = IP.from(targetIp.toJavaArray());

        // check whether we can handle the packet
        if (!skb.matchedIps.contains(ip)) {
            assert Logger.lowLevelDebug("no matched ip found for the arp packet");
            return;
        }

        // handle
        MacAddress mac = skb.table.ips.lookup(ip);

        assert Logger.lowLevelDebug("respond arp");
        ArpPacket resp = new ArpPacket();
        resp.setHardwareType(arp.getHardwareType());
        resp.setProtocolType(arp.getProtocolType());
        resp.setHardwareSize(arp.getHardwareSize());
        resp.setProtocolSize(arp.getProtocolSize());
        resp.setOpcode(Consts.ARP_PROTOCOL_OPCODE_RESP);
        resp.setSenderMac(mac.bytes);
        resp.setSenderIp(ByteArray.from(ip.getAddress()));
        resp.setTargetMac(arp.getSenderMac());
        resp.setTargetIp(arp.getSenderIp());

        EthernetPacket ether = new EthernetPacket();
        ether.setDst(skb.pkt.getSrc());
        ether.setSrc(mac);
        ether.setType(Consts.ETHER_TYPE_ARP);
        ether.setPacket(resp);

        skb.replacePacket(ether);

        directOutput(skb);
    }

    private void handleUnicastIP(SocketBuffer skb) {
        assert Logger.lowLevelDebug("handleUnicastIP(" + skb + ")");

        // check whether we can handle the packet
        var ipPkt = (AbstractIpPacket) skb.pkt.getPacket();

        // check whether we can handle the packet
        var dst = ipPkt.getDst();
        if (!skb.matchedIps.contains(dst)) {
            route(skb);
            return;
        }
        assert Logger.lowLevelDebug("no need to route the packet");

        if (ipPkt.getPacket() instanceof IcmpPacket) {
            // validate the icmp part
            var icmpPkt = (IcmpPacket) ipPkt.getPacket();
            if (ipPkt instanceof Ipv4Packet && icmpPkt.isIpv6()) {
                assert Logger.lowLevelDebug("drop the packet because it's icmpv6 inside ipv4");
                return;
            } else if (ipPkt instanceof Ipv6Packet && !icmpPkt.isIpv6()) {
                assert Logger.lowLevelDebug("drop the packet because it's icmpv4 inside ipv6");
                return;
            }

            if (icmpPkt.isIpv6()) {
                assert Logger.lowLevelDebug("is icmpv6");
                if (icmpPkt.getType() == Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Solicitation ||
                    icmpPkt.getType() == Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Advertisement) {
                    handleNDP(skb);
                    return;
                }
                assert Logger.lowLevelDebug("is not NDP");

                handleIcmpv6NotNDP(skb);
                return;
            } else {
                handleIcmpv4(skb);
                return;
            }
        } else if (ipPkt.getProtocol() == Consts.IP_PROTOCOL_TCP || ipPkt.getProtocol() == Consts.IP_PROTOCOL_UDP || ipPkt.getProtocol() == Consts.IP_PROTOCOL_SCTP) {
            if (L4.input(skb)) {
                return;
            }
            respondIcmpPortUnreachable(skb);
            return;
        } // else other packet types may be supported in the future
        assert Logger.lowLevelDebug("nothing to be done for the packet");
    }

    private void handleIcmpv4(SocketBuffer skb) {
        assert Logger.lowLevelDebug("handleIcmpv4(" + skb + ")");

        var ipPkt = (Ipv4Packet) skb.pkt.getPacket();
        var icmp = (IcmpPacket) ipPkt.getPacket();

        if (icmp.getType() != Consts.ICMP_PROTOCOL_TYPE_ECHO_REQ) {
            assert Logger.lowLevelDebug("cannot handle this type icmp packet");
            return;
        }

        respondIcmpPong(skb);
    }

    private void handleIcmpv6NotNDP(SocketBuffer skb) {
        assert Logger.lowLevelDebug("handleIcmpv6(" + skb + ")");

        var ipPkt = (Ipv6Packet) skb.pkt.getPacket();
        var icmp = (IcmpPacket) ipPkt.getPacket();

        if (icmp.getType() != Consts.ICMPv6_PROTOCOL_TYPE_ECHO_REQ) {
            assert Logger.lowLevelDebug("cannot handle this type icmp packet");
            return;
        }

        respondIcmpPong(skb);
    }

    private void handleNDP(SocketBuffer skb) {
        assert Logger.lowLevelDebug("handleNDP(" + skb + ")");
        var icmpv6 = (IcmpPacket) (((Ipv6Packet) skb.pkt.getPacket()).getPacket());
        if (icmpv6.getType() == Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Solicitation) {
            handleNeighborSolicitation(skb);
            return;
        }
        assert Logger.lowLevelDebug("cannot handle the ndp packet");
    }

    private void handleNeighborSolicitation(SocketBuffer skb) {
        assert Logger.lowLevelDebug("handleNeighborSolicitation(" + skb + ")");

        var inIpPkt = (Ipv6Packet) skb.pkt.getPacket();
        var inIcmp = (IcmpPacket) inIpPkt.getPacket();

        ByteArray other = inIcmp.getOther();
        if (other.length() < 20) { // 4 reserved and 16 target address
            assert Logger.lowLevelDebug("invalid packet for neighbor solicitation: too short");
            return;
        }
        assert Logger.lowLevelDebug("is a valid neighbor solicitation");

        byte[] targetAddr = other.sub(4, 16).toJavaArray();
        IPv6 ndpNeighborSolicitation = IP.fromIPv6(targetAddr);

        if (!skb.matchedIps.contains(ndpNeighborSolicitation)) {
            assert Logger.lowLevelDebug("this ndp ns does not request for matched synthetic ip");
            return;
        }

        MacAddress correspondingMac = skb.table.ips.lookup(ndpNeighborSolicitation);
        if (correspondingMac == null) {
            assert Logger.lowLevelDebug("requested ip is not synthetic ip");
            return;
        }

        respondIcmpNeighborAdvertisement(skb, ndpNeighborSolicitation, correspondingMac);
    }

    private void respondIcmpNeighborAdvertisement(SocketBuffer skb, IPv6 requestedIp, MacAddress requestedMac) {
        assert Logger.lowLevelDebug("respondIcmpNeighborAdvertisement(" + skb + "," + requestedIp + "," + requestedMac + ")");

        var inIpPkt = (Ipv6Packet) skb.pkt.getPacket();

        IcmpPacket icmp = new IcmpPacket(true);
        icmp.setType(Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Advertisement);
        icmp.setCode(0);
        icmp.setOther(
            (ByteArray.allocate(4).set(0, (byte) 0b01100000 /*-R,+S,+O*/)).concat(ByteArray.from(requestedIp.getAddress()))
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
        ipv6.setSrc(requestedIp);
        ipv6.setDst(inIpPkt.getSrc());
        ipv6.setExtHeaders(Collections.emptyList());
        ipv6.setPacket(icmp);
        ipv6.setPayloadLength(icmp.getRawICMPv6Packet(ipv6).length());

        EthernetPacket ether = new EthernetPacket();
        ether.setDst(skb.pkt.getSrc());
        ether.setSrc(requestedMac);
        ether.setType(Consts.ETHER_TYPE_IPv6);
        ether.setPacket(ipv6);

        skb.replacePacket(ether);

        directOutput(skb);
    }

    private void respondIcmpPong(SocketBuffer skb) {
        assert Logger.lowLevelDebug("respondIcmpPong(" + skb + ")");

        var inIpPkt = (AbstractIpPacket) skb.pkt.getPacket();
        var inIcmp = (IcmpPacket) inIpPkt.getPacket();

        var srcIp = inIpPkt.getDst();
        var srcMac = skb.table.ips.lookup(srcIp);
        if (srcMac == null) {
            Logger.shouldNotHappen("cannot find src mac for sending the icmp echo resp packet");
            return;
        }

        IcmpPacket icmp = new IcmpPacket(inIpPkt instanceof Ipv6Packet);
        icmp.setType(inIcmp.isIpv6() ? Consts.ICMPv6_PROTOCOL_TYPE_ECHO_RESP : Consts.ICMP_PROTOCOL_TYPE_ECHO_RESP);
        icmp.setCode(0);
        icmp.setOther(inIcmp.getOther());

        EthernetPacket ether = buildEtherIpIcmpPacket(skb.pkt.getSrc(), srcMac, srcIp, inIpPkt.getSrc(), icmp);
        skb.replacePacket(ether);

        routeOutput(skb);
    }

    private void respondIcmpPortUnreachable(SocketBuffer skb) {
        assert Logger.lowLevelDebug("respondIcmpPortUnreachable(" + skb + ")");

        var inIpPkt = (AbstractIpPacket) skb.pkt.getPacket();

        boolean isIpv6 = inIpPkt instanceof Ipv6Packet;
        var srcIp = inIpPkt.getDst();
        var srcMac = skb.table.ips.lookup(srcIp);
        if (srcMac == null) {
            Logger.shouldNotHappen("cannot find src mac for sending the icmp time exceeded packet");
            return;
        }
        // build the icmp time exceeded packet content
        var bytesOfTheOriginalIpPacket = inIpPkt.getRawPacket();
        var foo = new PacketBytes();
        foo.setBytes(ByteArray.allocate(0));
        inIpPkt.setPacket(foo);
        int headerLen = inIpPkt.getRawPacket().length();
        var bytesToSetIntoTheIcmpPacket = headerLen + 64;
        var toSet = bytesOfTheOriginalIpPacket;
        if (toSet.length() > bytesToSetIntoTheIcmpPacket) {
            toSet = toSet.sub(0, bytesToSetIntoTheIcmpPacket);
        }

        IcmpPacket icmp = new IcmpPacket(isIpv6);
        icmp.setType(isIpv6 ? Consts.ICMPv6_PROTOCOL_TYPE_DEST_UNREACHABLE : Consts.ICMP_PROTOCOL_TYPE_DEST_UNREACHABLE);
        icmp.setCode(isIpv6 ? Consts.ICMPv6_PROTOCOL_CODE_PORT_UNREACHABLE : Consts.ICMP_PROTOCOL_CODE_PORT_UNREACHABLE);
        icmp.setOther(
            ByteArray.allocate(4) // unused 4 bytes
                .concat(toSet)
        );

        EthernetPacket ether = buildEtherIpIcmpPacket(skb.pkt.getSrc(), srcMac, srcIp, inIpPkt.getSrc(), icmp);
        skb.replacePacket(ether);

        routeOutput(skb);
    }

    private void respondIcmpTimeExceeded(SocketBuffer skb) {
        assert Logger.lowLevelDebug("respondIcmpTimeExceeded(" + skb + ")");

        var inIpPkt = (AbstractIpPacket) skb.pkt.getPacket();
        boolean isIpv6 = inIpPkt instanceof Ipv6Packet;
        var srcIpAndMac = getRoutedSrcIpAndMac(skb.table, inIpPkt.getSrc());
        if (srcIpAndMac == null) {
            assert Logger.lowLevelDebug("cannot find src ip for sending the icmp time exceeded packet");
            return;
        }
        // build the icmp time exceeded packet content
        var bytesOfTheOriginalIpPacket = inIpPkt.getRawPacket();
        var foo = new PacketBytes();
        foo.setBytes(ByteArray.allocate(0));
        inIpPkt.setPacket(foo);
        int headerLen = inIpPkt.getRawPacket().length();
        var bytesToSetIntoTheIcmpPacket = headerLen + 64;
        var toSet = bytesOfTheOriginalIpPacket;
        if (toSet.length() > bytesToSetIntoTheIcmpPacket) {
            toSet = toSet.sub(0, bytesToSetIntoTheIcmpPacket);
        }

        IcmpPacket icmp = new IcmpPacket(isIpv6);
        icmp.setType(isIpv6 ? Consts.ICMPv6_PROTOCOL_TYPE_TIME_EXCEEDED : Consts.ICMP_PROTOCOL_TYPE_TIME_EXCEEDED);
        icmp.setCode(0);
        icmp.setOther(
            ByteArray.allocate(4) // unused 4 bytes
                .concat(toSet)
        );

        EthernetPacket ether = buildEtherIpIcmpPacket(skb.pkt.getSrc(), srcIpAndMac.mac, srcIpAndMac.ip, inIpPkt.getSrc(), icmp);
        skb.replacePacket(ether);

        routeOutput(skb);
    }

    private void route(SocketBuffer skb) {
        assert Logger.lowLevelDebug("route(" + skb + ")");

        var ippkt = (AbstractIpPacket) skb.pkt.getPacket();
        var dst = ippkt.getDst();

        // reduce ip packet hop
        {
            int hop = ippkt.getHopLimit();
            if (hop <= 1) {
                assert Logger.lowLevelDebug("hop too low, drop");
                respondIcmpTimeExceeded(skb);
                return;
            }
            hop -= 1;
            ippkt.setHopLimit(hop);
        }

        // find ruling rule for the dst
        var rule = skb.table.routeTable.lookup(dst);
        if (rule == null) {
            assert Logger.lowLevelDebug("no route rule found");
            return;
        }
        assert Logger.lowLevelDebug("route rule found");

        int vni = rule.toVni;
        if (vni == skb.vni) {
            // direct route
            assert Logger.lowLevelDebug("in the same vpc");

            MacAddress dstMac = skb.table.lookup(dst);
            if (dstMac == null) {
                assert Logger.lowLevelDebug("cannot find correct mac");
                broadcastArpOrNdp(skb.table, dst);
                return;
            }
            assert Logger.lowLevelDebug("found the correct mac");

            var srcMac = getRoutedSrcMac(skb.table, dst);
            if (srcMac == null) {
                assert Logger.lowLevelDebug("cannot route because src mac is not found");
                return;
            }

            skb.pkt.setSrc(srcMac);
            skb.pkt.setDst(dstMac);
            directOutput(skb);
        } else if (vni != 0) {
            // route to another network
            assert Logger.lowLevelDebug("routing to another vpc: " + vni);
            Table t = swCtx.getTable(vni);
            if (t == null) { // cannot handle if the table does no exist
                assert Logger.lowLevelDebug("target table " + vni + " is not found");
                return;
            }
            assert Logger.lowLevelDebug("target table is found");

            // get target mac
            var targetMac = getRoutedSrcMac(t, dst);
            if (targetMac == null) {
                assert Logger.lowLevelDebug("cannot route because target mac is not found");
                return;
            }

            skb.pkt.setSrc(targetMac);
            skb.pkt.setDst(targetMac);
            if (skb.vxlan != null) {
                skb.setVni(t.vni);
            }
            L2.input(skb);
        } else {
            // route based on ip
            var targetIp = rule.ip;
            assert Logger.lowLevelDebug("gateway rule");
            MacAddress dstMac = skb.table.lookup(targetIp);
            if (dstMac == null) {
                assert Logger.lowLevelDebug("mac not found in arp table, run a broadcast");
                broadcastArpOrNdp(skb.table, targetIp);
                return;
            }

            var srcMac = getRoutedSrcMac(skb.table, dst);
            if (srcMac == null) {
                assert Logger.lowLevelDebug("cannot route because src mac is not found");
                return;
            }

            skb.pkt.setSrc(srcMac);
            skb.pkt.setDst(dstMac);
            directOutput(skb);
        }
    }

    private void broadcastArpOrNdp(Table table, IP dst) {
        assert Logger.lowLevelDebug("getRoutedSrcMac(" + table + "," + dst + ")");
        if (dst instanceof IPv4) {
            broadcastArp(table, dst);
        } else {
            broadcastNdp(table, dst);
        }
    }

    private void broadcastArp(Table table, IP dst) {
        assert Logger.lowLevelDebug("broadcastArp(" + table + "," + dst + ")");

        EthernetPacket packet = buildArpReq(table, dst, new MacAddress("ff:ff:ff:ff:ff:ff"));
        if (packet == null) {
            assert Logger.lowLevelDebug("failed to build arp packet");
            return;
        }
        SocketBuffer skb = SocketBuffer.fromPacket(table, packet);
        directOutput(skb);
    }

    private void unicastArp(Table table, IP dst, MacAddress dstMac) {
        assert Logger.lowLevelDebug("unicastArp(" + table + "," + dst + "," + dstMac + ")");

        EthernetPacket packet = buildArpReq(table, dst, dstMac);
        if (packet == null) {
            assert Logger.lowLevelDebug("failed to build arp packet");
            return;
        }
        SocketBuffer skb = SocketBuffer.fromPacket(table, packet);
        directOutput(skb);
    }

    private void broadcastNdp(Table table, IP dst) {
        assert Logger.lowLevelDebug("broadcastNdp(" + table + "," + dst + ")");

        EthernetPacket packet = buildNdpNeighborSolicitation(table, dst, new MacAddress("ff:ff:ff:ff:ff:ff"));
        if (packet == null) {
            assert Logger.lowLevelDebug("failed to build ndp neighbor solicitation packet");
            return;
        }
        SocketBuffer skb = SocketBuffer.fromPacket(table, packet);
        directOutput(skb);
    }

    private void unicastNdp(Table table, IP dst, MacAddress dstMac) {
        assert Logger.lowLevelDebug("unicastNdp(" + table + "," + dst + "," + dstMac + ")");

        EthernetPacket packet = buildNdpNeighborSolicitation(table, dst, dstMac);
        if (packet == null) {
            assert Logger.lowLevelDebug("failed to build ndp neighbor solicitation packet");
            return;
        }
        SocketBuffer skb = SocketBuffer.fromPacket(table, packet);
        directOutput(skb);
    }

    private IPMac getRoutedSrcIpAndMac(Table table, IP dstIp) {
        assert Logger.lowLevelDebug("getRoutedSrcIpAndMac(" + table + "," + dstIp + ")");

        // find an ip in that table to be used for the src mac address
        var ipsInTable = table.ips.entries();
        boolean useIpv6 = dstIp instanceof IPv6;
        IPMac src = null;
        for (var x : ipsInTable) {
            if (useIpv6 && x.ip instanceof IPv6) {
                src = x;
                break;
            }
            if (!useIpv6 && x.ip instanceof IPv4) {
                src = x;
                break;
            }
        }
        return src;
    }

    private MacAddress getRoutedSrcMac(Table table, IP dstIp) {
        assert Logger.lowLevelDebug("getRoutedSrcMac(" + table + "," + dstIp + ")");
        var entry = getRoutedSrcIpAndMac(table, dstIp);
        if (entry == null) {
            return null;
        }
        return entry.mac;
    }

    private EthernetPacket buildEtherIpIcmpPacket(MacAddress dstMac, MacAddress srcMac, IP srcIp, IP dstIp, IcmpPacket icmp) {
        assert Logger.lowLevelDebug("buildIpIcmpPacket(" + dstMac + "," + srcMac + srcIp + "," + dstIp + icmp + ")");

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

        EthernetPacket ether = new EthernetPacket();
        ether.setDst(dstMac);
        ether.setSrc(srcMac);
        ether.setType(srcIp instanceof IPv4 ? Consts.ETHER_TYPE_IPv4 : Consts.ETHER_TYPE_IPv6);
        ether.setPacket(ipPkt);

        return ether;
    }

    private EthernetPacket buildArpReq(Table table, IP dstIp, MacAddress dstMac) {
        assert Logger.lowLevelDebug("buildArpReq(" + table + "," + dstIp + "," + dstMac + ")");

        var optIp = table.ips.entries().stream().filter(x -> x.ip instanceof IPv4).findAny();
        if (optIp.isEmpty()) {
            assert Logger.lowLevelDebug("cannot find synthetic ipv4 in the table");
            return null;
        }
        IP reqIp = optIp.get().ip;
        MacAddress reqMac = optIp.get().mac;

        ArpPacket req = new ArpPacket();
        req.setHardwareType(Consts.ARP_HARDWARE_TYPE_ETHER);
        req.setProtocolType(Consts.ARP_PROTOCOL_TYPE_IP);
        req.setHardwareSize(Consts.ARP_HARDWARE_TYPE_ETHER);
        req.setProtocolSize(Consts.ARP_PROTOCOL_TYPE_IP);
        req.setOpcode(Consts.ARP_PROTOCOL_OPCODE_REQ);
        req.setSenderMac(reqMac.bytes);
        req.setSenderIp(ByteArray.from(reqIp.getAddress()));
        req.setTargetMac(ByteArray.allocate(6));
        req.setTargetIp(ByteArray.from(dstIp.getAddress()));

        EthernetPacket ether = new EthernetPacket();
        ether.setDst(dstMac);
        ether.setSrc(reqMac);
        ether.setType(Consts.ETHER_TYPE_ARP);
        ether.setPacket(req);

        return ether;
    }

    private EthernetPacket buildNdpNeighborSolicitation(Table table, IP dstIp, MacAddress dstMac) {
        assert Logger.lowLevelDebug("buildNdpNeighborSolicitation(" + table + "," + dstIp + "," + dstMac + ")");

        var optIp = table.ips.entries().stream().filter(x -> x.ip instanceof IPv6).findAny();
        if (optIp.isEmpty()) {
            assert Logger.lowLevelDebug("cannot find synthetic ipv4 in the table");
            return null;
        }
        IP reqIp = optIp.get().ip;
        MacAddress reqMac = optIp.get().mac;

        IcmpPacket icmp = new IcmpPacket(true);
        icmp.setType(Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Solicitation);
        icmp.setCode(0);
        icmp.setOther(
            (ByteArray.allocate(4).set(0, (byte) 0)).concat(ByteArray.from(dstIp.getAddress()))
                .concat(( // the source link-layer address
                    ByteArray.allocate(1 + 1).set(0, (byte) Consts.ICMPv6_OPTION_TYPE_Source_Link_Layer_Address)
                        .set(1, (byte) 1) // mac address len = 6, (1 + 1 + 6)/8 = 1
                        .concat(reqMac.bytes)
                ))
        );

        Ipv6Packet ipv6 = new Ipv6Packet();
        ipv6.setVersion(6);
        ipv6.setNextHeader(Consts.IP_PROTOCOL_ICMPv6);
        ipv6.setHopLimit(255);
        ipv6.setSrc((IPv6) reqIp);
        byte[] foo = Consts.IPv6_Solicitation_Node_Multicast_Address.toNewJavaArray();
        byte[] bar = dstIp.getAddress();
        foo[13] = bar[13];
        foo[14] = bar[14];
        foo[15] = bar[15];
        ipv6.setDst(IP.fromIPv6(foo));
        ipv6.setExtHeaders(Collections.emptyList());
        ipv6.setPacket(icmp);
        ipv6.setPayloadLength(icmp.getRawICMPv6Packet(ipv6).length());

        EthernetPacket ether = new EthernetPacket();
        ether.setDst(dstMac);
        ether.setSrc(reqMac);
        ether.setType(Consts.ETHER_TYPE_IPv6);
        ether.setPacket(ipv6);

        return ether;
    }

    private void directOutput(SocketBuffer skb) {
        assert Logger.lowLevelDebug("directOutput(" + skb + ")");
        L2.output(skb);
    }

    private void routeOutput(SocketBuffer skb) {
        assert Logger.lowLevelDebug("routeOutput(" + skb + ")");

        if (!skb.pkt.getDst().isUnicast()) {
            assert Logger.lowLevelDebug("packet is not unicast, no need to route");
            directOutput(skb);
            return;
        }

        var ipPkt = (AbstractIpPacket) skb.pkt.getPacket();
        var dst = ipPkt.getDst();
        var routeRule = skb.table.routeTable.lookup(dst);
        if (routeRule == null) {
            assert Logger.lowLevelDebug("no route rule found for the ip dst, no need to route");
            directOutput(skb);
            return;
        }

        if (routeRule.toVni == skb.vni) {
            assert Logger.lowLevelDebug("direct route, no changes required");
            directOutput(skb);
        } else if (routeRule.toVni != 0) {
            assert Logger.lowLevelDebug("route to another vpc");

            // search for any synthetic ip in the target vpc
            Table targetTable = swCtx.getTable(routeRule.toVni);
            if (targetTable == null) {
                assert Logger.lowLevelDebug("target vpc not found");
                return;
            }
            MacAddress targetMac = getRoutedSrcMac(targetTable, dst);
            if (targetMac == null) {
                assert Logger.lowLevelDebug("cannot find dst mac for sending this packet to another vpc");
                return;
            }
            skb.pkt.setSrc(targetMac);
            skb.pkt.setDst(targetMac);
            skb.setTable(targetTable);
            L2.input(skb);
        } else {
            assert Logger.lowLevelDebug("route based on ip");

            var targetIp = routeRule.ip;
            MacAddress dstMac = skb.table.lookup(targetIp);
            if (dstMac == null) {
                assert Logger.lowLevelDebug("mac not found in arp table, run a broadcast");
                broadcastArpOrNdp(skb.table, targetIp);
                return;
            }

            skb.pkt.setDst(dstMac);
            directOutput(skb);
        }
    }

    public void output(SocketBuffer skb) {
        assert Logger.lowLevelDebug("L3.output(" + skb + ")");

        // assign mac to the packet
        MacAddress srcMac = skb.table.ips.lookup(skb.ipPkt.getSrc());
        if (srcMac == null) {
            Logger.shouldNotHappen("cannot find synthetic ip for sending the output packet");
            return;
        }
        MacAddress dstMac;

        // check routing rule
        var rule = skb.table.routeTable.lookup(skb.ipPkt.getDst());
        if (rule == null || rule.toVni == skb.vni) {
            assert Logger.lowLevelDebug("no route rule or route to current network");

            // try to find the mac address of the dst
            dstMac = skb.table.lookup(skb.ipPkt.getDst());

            if (dstMac == null) {
                assert Logger.lowLevelDebug("cannot find dst mac for sending the packet");
                broadcastArpOrNdp(skb.table, skb.ipPkt.getDst());
                return;
            }
        } else {
            assert Logger.lowLevelDebug("need to do routing");
            // the dstMac is not important, will be filled
            dstMac = new MacAddress(ByteArray.allocate(6));
        }

        // form a ethernet packet
        EthernetPacket ether = new EthernetPacket();
        ether.setDst(dstMac);
        ether.setSrc(srcMac);
        ether.setType((skb.ipPkt instanceof Ipv4Packet) ? Consts.ETHER_TYPE_IPv4 : Consts.ETHER_TYPE_IPv6);
        ether.setPacket(skb.ipPkt);

        skb.replacePacket(ether);

        // route out
        routeOutput(skb);
    }
}
