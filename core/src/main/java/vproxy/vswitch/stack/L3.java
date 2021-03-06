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
import vproxy.vswitch.PacketBuffer;
import vproxy.vswitch.SwitchContext;
import vproxy.vswitch.Table;
import vproxy.vswitch.util.SwitchUtils;

public class L3 {
    private final SwitchContext swCtx;
    private final L2 L2;
    public final L4 L4;

    public L3(SwitchContext swCtx, L2 l2) {
        this.swCtx = swCtx;
        L2 = l2;
        L4 = new L4(swCtx, this);
    }

    public void input(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("L3.input(" + pkb + ")");
        if (pkb.pkt.getDst().isUnicast()) {
            handleInputUnicast(pkb);
        } else {
            handleInputBroadcast(pkb);
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

    private void handleInputUnicast(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("handleInputUnicast(" + pkb + ")");

        var packet = pkb.pkt.getPacket();
        if (packet instanceof ArpPacket) {
            handleArp(pkb);
            return;
        } else if (packet instanceof AbstractIpPacket) {
            handleUnicastIP(pkb);
            return;
        }
        assert Logger.lowLevelDebug("cannot process the packet");
    }

    private void handleInputBroadcast(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("handleInputBroadcast(" + pkb + ")");

        var packet = pkb.pkt.getPacket();
        if (packet instanceof ArpPacket) {
            handleArp(pkb);
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
            handleNeighborSolicitation(pkb);
            return;
        }
        assert Logger.lowLevelDebug("cannot process the packet");
    }

    private void handleArp(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("handleArp(" + pkb + ")");

        ArpPacket arp = (ArpPacket) pkb.pkt.getPacket();
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
        if (!pkb.matchedIps.contains(ip)) {
            assert Logger.lowLevelDebug("no matched ip found for the arp packet");
            return;
        }

        // handle
        MacAddress mac = pkb.table.ips.lookup(ip);

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

        EthernetPacket ether = SwitchUtils.buildEtherArpPacket(pkb.pkt.getSrc(), mac, resp);

        pkb.replacePacket(ether);

        directOutput(pkb);
    }

    private void handleUnicastIP(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("handleUnicastIP(" + pkb + ")");

        // check whether we can handle the packet
        var ipPkt = (AbstractIpPacket) pkb.pkt.getPacket();

        // check whether we can handle the packet
        var dst = ipPkt.getDst();
        if (!pkb.matchedIps.contains(dst)) {
            route(pkb);
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
                    handleNDP(pkb);
                    return;
                }
                assert Logger.lowLevelDebug("is not NDP");

                handleIcmpv6NotNDP(pkb);
                return;
            } else {
                handleIcmpv4(pkb);
                return;
            }
        } else if (ipPkt.getProtocol() == Consts.IP_PROTOCOL_TCP || ipPkt.getProtocol() == Consts.IP_PROTOCOL_UDP || ipPkt.getProtocol() == Consts.IP_PROTOCOL_SCTP) {
            if (L4.input(pkb)) {
                return;
            }
            respondIcmpPortUnreachable(pkb);
            return;
        } // else other packet types may be supported in the future
        assert Logger.lowLevelDebug("nothing to be done for the packet");
    }

    private void handleIcmpv4(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("handleIcmpv4(" + pkb + ")");

        var ipPkt = (Ipv4Packet) pkb.pkt.getPacket();
        var icmp = (IcmpPacket) ipPkt.getPacket();

        if (icmp.getType() != Consts.ICMP_PROTOCOL_TYPE_ECHO_REQ) {
            assert Logger.lowLevelDebug("cannot handle this type icmp packet");
            return;
        }

        respondIcmpPong(pkb);
    }

    private void handleIcmpv6NotNDP(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("handleIcmpv6(" + pkb + ")");

        var ipPkt = (Ipv6Packet) pkb.pkt.getPacket();
        var icmp = (IcmpPacket) ipPkt.getPacket();

        if (icmp.getType() != Consts.ICMPv6_PROTOCOL_TYPE_ECHO_REQ) {
            assert Logger.lowLevelDebug("cannot handle this type icmp packet");
            return;
        }

        respondIcmpPong(pkb);
    }

    private void handleNDP(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("handleNDP(" + pkb + ")");
        var icmpv6 = (IcmpPacket) (((Ipv6Packet) pkb.pkt.getPacket()).getPacket());
        if (icmpv6.getType() == Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Solicitation) {
            handleNeighborSolicitation(pkb);
            return;
        }
        assert Logger.lowLevelDebug("cannot handle the ndp packet");
    }

    private void handleNeighborSolicitation(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("handleNeighborSolicitation(" + pkb + ")");

        var inIpPkt = (Ipv6Packet) pkb.pkt.getPacket();
        var inIcmp = (IcmpPacket) inIpPkt.getPacket();

        IPv6 ndpNeighborSolicitation = SwitchUtils.extractTargetAddressFromNeighborSolicitation(inIcmp);
        if (ndpNeighborSolicitation == null) {
            return;
        }

        if (!pkb.matchedIps.contains(ndpNeighborSolicitation)) {
            assert Logger.lowLevelDebug("this ndp ns does not request for matched synthetic ip");
            return;
        }

        MacAddress correspondingMac = pkb.table.ips.lookup(ndpNeighborSolicitation);
        if (correspondingMac == null) {
            assert Logger.lowLevelDebug("requested ip is not synthetic ip");
            return;
        }

        respondIcmpNeighborAdvertisement(pkb, ndpNeighborSolicitation, correspondingMac);
    }

    private void respondIcmpNeighborAdvertisement(PacketBuffer pkb, IPv6 requestedIp, MacAddress requestedMac) {
        assert Logger.lowLevelDebug("respondIcmpNeighborAdvertisement(" + pkb + "," + requestedIp + "," + requestedMac + ")");

        var inIpPkt = (Ipv6Packet) pkb.pkt.getPacket();

        var ipicmp = SwitchUtils.buildNeighborAdvertisementPacket(requestedMac, requestedIp, inIpPkt.getSrc());
        var ether = SwitchUtils.buildEtherIpPacket(pkb.pkt.getSrc(), requestedMac, ipicmp);

        pkb.replacePacket(ether);

        directOutput(pkb);
    }

    private void respondIcmpPong(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("respondIcmpPong(" + pkb + ")");

        var inIpPkt = (AbstractIpPacket) pkb.pkt.getPacket();
        var inIcmp = (IcmpPacket) inIpPkt.getPacket();

        var srcIp = inIpPkt.getDst();
        var srcMac = pkb.table.ips.lookup(srcIp);
        if (srcMac == null) {
            Logger.shouldNotHappen("cannot find src mac for sending the icmp echo resp packet");
            return;
        }

        IcmpPacket icmp = new IcmpPacket(inIpPkt instanceof Ipv6Packet);
        icmp.setType(inIcmp.isIpv6() ? Consts.ICMPv6_PROTOCOL_TYPE_ECHO_RESP : Consts.ICMP_PROTOCOL_TYPE_ECHO_RESP);
        icmp.setCode(0);
        icmp.setOther(inIcmp.getOther());

        EthernetPacket ether = buildEtherIpIcmpPacket(pkb.pkt.getSrc(), srcMac, srcIp, inIpPkt.getSrc(), icmp);
        pkb.replacePacket(ether);

        routeOutput(pkb);
    }

    private void respondIcmpPortUnreachable(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("respondIcmpPortUnreachable(" + pkb + ")");

        var inIpPkt = (AbstractIpPacket) pkb.pkt.getPacket();

        boolean isIpv6 = inIpPkt instanceof Ipv6Packet;
        var srcIp = inIpPkt.getDst();
        var srcMac = pkb.table.ips.lookup(srcIp);
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

        EthernetPacket ether = buildEtherIpIcmpPacket(pkb.pkt.getSrc(), srcMac, srcIp, inIpPkt.getSrc(), icmp);
        pkb.replacePacket(ether);

        routeOutput(pkb);
    }

    private void respondIcmpTimeExceeded(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("respondIcmpTimeExceeded(" + pkb + ")");

        var inIpPkt = (AbstractIpPacket) pkb.pkt.getPacket();
        boolean isIpv6 = inIpPkt instanceof Ipv6Packet;
        var srcIpAndMac = getRoutedSrcIpAndMac(pkb.table, inIpPkt.getSrc());
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

        EthernetPacket ether = buildEtherIpIcmpPacket(pkb.pkt.getSrc(), srcIpAndMac.mac, srcIpAndMac.ip, inIpPkt.getSrc(), icmp);
        pkb.replacePacket(ether);

        routeOutput(pkb);
    }

    private void route(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("route(" + pkb + ")");

        var ippkt = (AbstractIpPacket) pkb.pkt.getPacket();
        var dst = ippkt.getDst();

        // reduce ip packet hop
        {
            int hop = ippkt.getHopLimit();
            if (hop <= 1) {
                assert Logger.lowLevelDebug("hop too low, drop");
                respondIcmpTimeExceeded(pkb);
                return;
            }
            hop -= 1;
            ippkt.setHopLimit(hop);
        }

        // find ruling rule for the dst
        var rule = pkb.table.routeTable.lookup(dst);
        if (rule == null) {
            assert Logger.lowLevelDebug("no route rule found");
            return;
        }
        assert Logger.lowLevelDebug("route rule found: " + rule);

        int vni = rule.toVni;
        if (vni == pkb.vni) {
            // direct route
            assert Logger.lowLevelDebug("in the same vpc");

            MacAddress dstMac = pkb.table.lookup(dst);
            if (dstMac == null) {
                assert Logger.lowLevelDebug("cannot find correct mac");
                broadcastArpOrNdp(pkb.table, dst);
                return;
            }
            assert Logger.lowLevelDebug("found the correct mac");

            var srcMac = getRoutedSrcMac(pkb.table, dst);
            if (srcMac == null) {
                assert Logger.lowLevelDebug("cannot route because src mac is not found");
                return;
            }

            pkb.pkt.setSrc(srcMac);
            pkb.pkt.setDst(dstMac);
            directOutput(pkb);
        } else if (vni != 0) {
            // route to another network
            assert Logger.lowLevelDebug("routing to another vpc: " + vni);
            Table t = swCtx.getTable(vni);
            if (t == null) { // cannot handle if the table does no exist
                assert Logger.lowLevelDebug("target table " + vni + " is not found");
                return;
            }
            assert Logger.lowLevelDebug("target table is found");

            // get src mac
            var newSrcMac = getRoutedSrcMac(t, dst);
            if (newSrcMac == null) {
                assert Logger.lowLevelDebug("cannot route because source mac is not found");
                return;
            }

            var targetRule = t.routeTable.lookup(dst);
            if (!targetRule.isLocalDirect(t.vni)) {
                assert Logger.lowLevelDebug("still require routing after switching the table");
                pkb.setTable(t);
                pkb.pkt.setSrc(newSrcMac);
                routeOutput(pkb);
                return;
            }

            assert Logger.lowLevelDebug("direct route after switching the table");

            // get target mac
            var targetMac = t.lookup(dst);
            if (targetMac == null) {
                assert Logger.lowLevelDebug("cannot route because dest mac is not found");
                broadcastArpOrNdp(t, dst);
                return;
            }
            assert Logger.lowLevelDebug("found dst mac: " + targetMac);

            pkb.pkt.setSrc(newSrcMac);
            pkb.pkt.setDst(targetMac);
            pkb.setTable(t);
            L2.input(pkb);
        } else {
            // route based on ip
            var targetIp = rule.ip;
            assert Logger.lowLevelDebug("gateway rule");
            MacAddress dstMac = pkb.table.lookup(targetIp);
            if (dstMac == null) {
                assert Logger.lowLevelDebug("mac not found in arp table, run a broadcast");
                broadcastArpOrNdp(pkb.table, targetIp);
                return;
            }

            var srcMac = getRoutedSrcMac(pkb.table, dst);
            if (srcMac == null) {
                assert Logger.lowLevelDebug("cannot route because src mac is not found");
                return;
            }

            pkb.pkt.setSrc(srcMac);
            pkb.pkt.setDst(dstMac);
            directOutput(pkb);
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

        EthernetPacket packet = buildArpReq(table, dst, SwitchUtils.BROADCAST_MAC);
        if (packet == null) {
            assert Logger.lowLevelDebug("failed to build arp packet");
            return;
        }
        PacketBuffer pkb = PacketBuffer.fromPacket(table, packet);
        directOutput(pkb);
    }

    private void unicastArp(Table table, IP dst, MacAddress dstMac) {
        assert Logger.lowLevelDebug("unicastArp(" + table + "," + dst + "," + dstMac + ")");

        EthernetPacket packet = buildArpReq(table, dst, dstMac);
        if (packet == null) {
            assert Logger.lowLevelDebug("failed to build arp packet");
            return;
        }
        PacketBuffer pkb = PacketBuffer.fromPacket(table, packet);
        directOutput(pkb);
    }

    private void broadcastNdp(Table table, IP dst) {
        assert Logger.lowLevelDebug("broadcastNdp(" + table + "," + dst + ")");

        EthernetPacket packet = buildNdpNeighborSolicitation(table, dst, SwitchUtils.BROADCAST_MAC);
        if (packet == null) {
            assert Logger.lowLevelDebug("failed to build ndp neighbor solicitation packet");
            return;
        }
        PacketBuffer pkb = PacketBuffer.fromPacket(table, packet);
        directOutput(pkb);
    }

    private void unicastNdp(Table table, IP dst, MacAddress dstMac) {
        assert Logger.lowLevelDebug("unicastNdp(" + table + "," + dst + "," + dstMac + ")");

        EthernetPacket packet = buildNdpNeighborSolicitation(table, dst, dstMac);
        if (packet == null) {
            assert Logger.lowLevelDebug("failed to build ndp neighbor solicitation packet");
            return;
        }
        PacketBuffer pkb = PacketBuffer.fromPacket(table, packet);
        directOutput(pkb);
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
        return SwitchUtils.buildEtherIpIcmpPacket(dstMac, srcMac, srcIp, dstIp, icmp);
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
        req.setHardwareSize(6);
        req.setProtocolSize(4);
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

        Ipv6Packet ipv6 = SwitchUtils.buildNeighborSolicitationPacket((IPv6) dstIp, reqMac, (IPv6) reqIp);

        EthernetPacket ether = new EthernetPacket();
        ether.setDst(dstMac);
        ether.setSrc(reqMac);
        ether.setType(Consts.ETHER_TYPE_IPv6);
        ether.setPacket(ipv6);

        return ether;
    }

    private void directOutput(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("directOutput(" + pkb + ")");
        L2.output(pkb);
    }

    private void routeOutput(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("routeOutput(" + pkb + ")");

        if (!pkb.pkt.getDst().isUnicast()) {
            assert Logger.lowLevelDebug("packet is not unicast, no need to route");
            directOutput(pkb);
            return;
        }

        var ipPkt = (AbstractIpPacket) pkb.pkt.getPacket();
        var dst = ipPkt.getDst();
        var routeRule = pkb.table.routeTable.lookup(dst);
        if (routeRule == null) {
            assert Logger.lowLevelDebug("no route rule found for the ip dst, no need to route");
            directOutput(pkb);
            return;
        }

        assert Logger.lowLevelDebug("route rule found: " + routeRule);

        if (routeRule.toVni == pkb.vni) {
            assert Logger.lowLevelDebug("direct route, no changes required");
            directOutput(pkb);
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
            pkb.pkt.setSrc(targetMac);
            pkb.pkt.setDst(targetMac);
            pkb.setTable(targetTable);
            L2.input(pkb);
        } else {
            assert Logger.lowLevelDebug("route based on ip");

            var targetIp = routeRule.ip;
            MacAddress dstMac = pkb.table.lookup(targetIp);
            if (dstMac == null) {
                assert Logger.lowLevelDebug("mac not found in arp table, run a broadcast");
                broadcastArpOrNdp(pkb.table, targetIp);
                return;
            }

            pkb.pkt.setDst(dstMac);
            directOutput(pkb);
        }
    }

    public void output(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("L3.output(" + pkb + ")");

        // assign mac to the packet
        MacAddress srcMac = pkb.table.ips.lookup(pkb.ipPkt.getSrc());
        if (srcMac == null) {
            Logger.shouldNotHappen("cannot find synthetic ip for sending the output packet");
            return;
        }
        MacAddress dstMac;

        // check routing rule
        var rule = pkb.table.routeTable.lookup(pkb.ipPkt.getDst());
        if (rule == null || rule.toVni == pkb.vni) {
            assert Logger.lowLevelDebug("no route rule or route to current network");

            // try to find the mac address of the dst
            dstMac = pkb.table.lookup(pkb.ipPkt.getDst());

            if (dstMac == null) {
                assert Logger.lowLevelDebug("cannot find dst mac for sending the packet");
                broadcastArpOrNdp(pkb.table, pkb.ipPkt.getDst());
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
        ether.setType((pkb.ipPkt instanceof Ipv4Packet) ? Consts.ETHER_TYPE_IPv4 : Consts.ETHER_TYPE_IPv6);
        ether.setPacket(pkb.ipPkt);

        pkb.replacePacket(ether);

        // route out
        routeOutput(pkb);
    }
}
