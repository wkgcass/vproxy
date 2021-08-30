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
import vproxy.vswitch.VirtualNetwork;
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

    public void resolve(VirtualNetwork network, IP ip, MacAddress knownMac) {
        assert Logger.lowLevelDebug("lookupAddress(" + network + "," + ip + "," + knownMac + ")");
        if (ip instanceof IPv4) {
            arpResolve(network, ip, knownMac);
        } else {
            ndpResolve(network, ip, knownMac);
        }
    }

    private void arpResolve(VirtualNetwork network, IP ip, MacAddress knownMac) {
        assert Logger.lowLevelDebug("lookupAddress(" + network + "," + ip + "," + knownMac + ")");
        var iface = knownMac == null ? null : network.macTable.lookup(knownMac);
        if (iface == null) {
            assert Logger.lowLevelDebug(" cannot find iface of the mac, try broadcast");
            broadcastArp(network, ip);
        } else {
            assert Logger.lowLevelDebug(" run unicast");
            unicastArp(network, ip, knownMac);
        }
    }

    private void ndpResolve(VirtualNetwork network, IP ip, MacAddress knownMac) {
        assert Logger.lowLevelDebug("lookupAddress(" + network + "," + ip + "," + knownMac + ")");
        var iface = network.macTable.lookup(knownMac);
        if (iface == null) {
            assert Logger.lowLevelDebug("cannot find iface of the mac, try broadcast");
            broadcastNdp(network, ip);
        } else {
            assert Logger.lowLevelDebug("run unicast");
            unicastNdp(network, ip, knownMac);
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
        MacAddress mac = pkb.network.ips.lookup(ip);

        assert Logger.lowLevelDebug("respond arp");
        ArpPacket resp = new ArpPacket();
        resp.setHardwareType(arp.getHardwareType());
        resp.setProtocolType(arp.getProtocolType());
        resp.setHardwareSize(arp.getHardwareSize());
        resp.setProtocolSize(arp.getProtocolSize());
        resp.setOpcode(Consts.ARP_PROTOCOL_OPCODE_RESP);
        resp.setSenderMac(mac.bytes);
        resp.setSenderIp(ip.bytes);
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
        if (pkb.ensurePartialPacketParsed()) return;
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

        MacAddress correspondingMac = pkb.network.ips.lookup(ndpNeighborSolicitation);
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
        if (pkb.ensurePartialPacketParsed()) return;
        assert Logger.lowLevelDebug("respondIcmpPong(" + pkb + ")");

        var inIpPkt = (AbstractIpPacket) pkb.pkt.getPacket();
        var inIcmp = (IcmpPacket) inIpPkt.getPacket();

        var srcIp = inIpPkt.getDst();
        var srcMac = pkb.network.ips.lookup(srcIp);
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
        if (pkb.ensurePartialPacketParsed()) return;
        assert Logger.lowLevelDebug("respondIcmpPortUnreachable(" + pkb + ")");

        var inIpPkt = (AbstractIpPacket) pkb.pkt.getPacket();

        boolean isIpv6 = inIpPkt instanceof Ipv6Packet;
        var srcIp = inIpPkt.getDst();
        var srcMac = pkb.network.ips.lookup(srcIp);
        if (srcMac == null) {
            Logger.shouldNotHappen("cannot find src mac for sending the icmp time exceeded packet");
            return;
        }
        // build the icmp time exceeded packet content
        var bytesOfTheOriginalIpPacket = inIpPkt.getRawPacket(AbstractPacket.FLAG_CHECKSUM_UNNECESSARY);
        var foo = new PacketBytes();
        foo.setBytes(ByteArray.allocate(0));
        inIpPkt.setPacket(foo);
        int headerLen = inIpPkt.getRawPacket(AbstractPacket.FLAG_CHECKSUM_UNNECESSARY).length();
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
        if (pkb.ensurePartialPacketParsed()) return;
        assert Logger.lowLevelDebug("respondIcmpTimeExceeded(" + pkb + ")");

        var inIpPkt = (AbstractIpPacket) pkb.pkt.getPacket();
        boolean isIpv6 = inIpPkt instanceof Ipv6Packet;
        var srcIpAndMac = getRoutedSrcIpAndMac(pkb.network, inIpPkt.getSrc());
        if (srcIpAndMac == null) {
            assert Logger.lowLevelDebug("cannot find src ip for sending the icmp time exceeded packet");
            return;
        }
        // build the icmp time exceeded packet content
        var bytesOfTheOriginalIpPacket = inIpPkt.getRawPacket(AbstractPacket.FLAG_CHECKSUM_UNNECESSARY);
        var foo = new PacketBytes();
        foo.setBytes(ByteArray.allocate(0));
        inIpPkt.setPacket(foo);
        int headerLen = inIpPkt.getRawPacket(AbstractPacket.FLAG_CHECKSUM_UNNECESSARY).length();
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

        if (!ippkt.getDst().isUnicast()) {
            assert Logger.lowLevelDebug("ip packet to be routed must be unicast, drop this packet");
            return;
        }
        if (ippkt.getPacket() instanceof IcmpPacket &&
            (
                ((IcmpPacket) ippkt.getPacket()).getType() == Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Solicitation ||
                    ((IcmpPacket) ippkt.getPacket()).getType() == Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Advertisement
            )
        ) {
            assert Logger.lowLevelDebug("ndp ns/na cannot be routed, drop this packet");
            return;
        }

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
        var rule = pkb.network.routeTable.lookup(dst);
        if (rule == null) {
            assert Logger.lowLevelDebug("no route rule found");
            return;
        }
        assert Logger.lowLevelDebug("route rule found: " + rule);

        int vni = rule.toVni;
        if (vni == pkb.vni) {
            // direct route
            assert Logger.lowLevelDebug("in the same vpc");

            MacAddress dstMac = pkb.network.lookup(dst);
            if (dstMac == null) {
                assert Logger.lowLevelDebug("cannot find correct mac");
                broadcastArpOrNdp(pkb.network, dst);
                return;
            }
            assert Logger.lowLevelDebug("found the correct mac");

            var srcMac = getRoutedSrcMac(pkb.network, dst);
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
            VirtualNetwork n = swCtx.getNetwork(vni);
            if (n == null) { // cannot handle if the network does no exist
                assert Logger.lowLevelDebug("target network " + vni + " is not found");
                return;
            }
            assert Logger.lowLevelDebug("target network is found");

            // get src mac
            var newSrcMac = getRoutedSrcMac(n, dst);
            if (newSrcMac == null) {
                assert Logger.lowLevelDebug("cannot route because source mac is not found");
                return;
            }

            var targetRule = n.routeTable.lookup(dst);
            if (!targetRule.isLocalDirect(n.vni)) {
                assert Logger.lowLevelDebug("still require routing after switching the network");
                pkb.setNetwork(n);
                pkb.pkt.setSrc(newSrcMac);
                routeOutput(pkb);
                return;
            }

            assert Logger.lowLevelDebug("direct route after switching the network");

            // get target mac
            var targetMac = n.lookup(dst);
            if (targetMac == null) {
                assert Logger.lowLevelDebug("cannot route because dest mac is not found");
                broadcastArpOrNdp(n, dst);
                return;
            }
            assert Logger.lowLevelDebug("found dst mac: " + targetMac);

            pkb.pkt.setSrc(newSrcMac);
            pkb.pkt.setDst(targetMac);
            pkb.setNetwork(n);
            L2.reinput(pkb);
        } else {
            // route based on ip
            var targetIp = rule.ip;
            assert Logger.lowLevelDebug("gateway rule");
            MacAddress dstMac = pkb.network.lookup(targetIp);
            if (dstMac == null) {
                assert Logger.lowLevelDebug("mac not found in arp table, run a broadcast");
                broadcastArpOrNdp(pkb.network, targetIp);
                return;
            }

            var srcMac = getRoutedSrcMac(pkb.network, dst);
            if (srcMac == null) {
                assert Logger.lowLevelDebug("cannot route because src mac is not found");
                return;
            }

            pkb.pkt.setSrc(srcMac);
            pkb.pkt.setDst(dstMac);
            directOutput(pkb);
        }
    }

    private void broadcastArpOrNdp(VirtualNetwork network, IP dst) {
        assert Logger.lowLevelDebug("getRoutedSrcMac(" + network + "," + dst + ")");
        if (dst instanceof IPv4) {
            broadcastArp(network, dst);
        } else {
            broadcastNdp(network, dst);
        }
    }

    private void broadcastArp(VirtualNetwork network, IP dst) {
        assert Logger.lowLevelDebug("broadcastArp(" + network + "," + dst + ")");

        EthernetPacket packet = buildArpReq(network, dst, SwitchUtils.BROADCAST_MAC);
        if (packet == null) {
            assert Logger.lowLevelDebug("failed to build arp packet");
            return;
        }
        PacketBuffer pkb = PacketBuffer.fromPacket(network, packet);
        directOutput(pkb);
    }

    private void unicastArp(VirtualNetwork network, IP dst, MacAddress dstMac) {
        assert Logger.lowLevelDebug("unicastArp(" + network + "," + dst + "," + dstMac + ")");

        EthernetPacket packet = buildArpReq(network, dst, dstMac);
        if (packet == null) {
            assert Logger.lowLevelDebug("failed to build arp packet");
            return;
        }
        PacketBuffer pkb = PacketBuffer.fromPacket(network, packet);
        directOutput(pkb);
    }

    private void broadcastNdp(VirtualNetwork network, IP dst) {
        assert Logger.lowLevelDebug("broadcastNdp(" + network + "," + dst + ")");

        EthernetPacket packet = buildNdpNeighborSolicitation(network, dst, SwitchUtils.BROADCAST_MAC);
        if (packet == null) {
            assert Logger.lowLevelDebug("failed to build ndp neighbor solicitation packet");
            return;
        }
        PacketBuffer pkb = PacketBuffer.fromPacket(network, packet);
        directOutput(pkb);
    }

    private void unicastNdp(VirtualNetwork network, IP dst, MacAddress dstMac) {
        assert Logger.lowLevelDebug("unicastNdp(" + network + "," + dst + "," + dstMac + ")");

        EthernetPacket packet = buildNdpNeighborSolicitation(network, dst, dstMac);
        if (packet == null) {
            assert Logger.lowLevelDebug("failed to build ndp neighbor solicitation packet");
            return;
        }
        PacketBuffer pkb = PacketBuffer.fromPacket(network, packet);
        directOutput(pkb);
    }

    private IPMac getRoutedSrcIpAndMac(VirtualNetwork network, IP dstIp) {
        assert Logger.lowLevelDebug("getRoutedSrcIpAndMac(" + network + "," + dstIp + ")");

        // find an ip in that network to be used for the src mac address
        if (dstIp instanceof IPv4) {
            return network.ips.findAnyIPv4ForRouting();
        } else {
            return network.ips.findAnyIPv6ForRouting();
        }
    }

    private MacAddress getRoutedSrcMac(VirtualNetwork network, IP dstIp) {
        assert Logger.lowLevelDebug("getRoutedSrcMac(" + network + "," + dstIp + ")");
        var entry = getRoutedSrcIpAndMac(network, dstIp);
        if (entry == null) {
            return null;
        }
        return entry.mac;
    }

    private EthernetPacket buildEtherIpIcmpPacket(MacAddress dstMac, MacAddress srcMac, IP srcIp, IP dstIp, IcmpPacket icmp) {
        return SwitchUtils.buildEtherIpIcmpPacket(dstMac, srcMac, srcIp, dstIp, icmp);
    }

    private EthernetPacket buildArpReq(VirtualNetwork network, IP dstIp, MacAddress dstMac) {
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

    private EthernetPacket buildNdpNeighborSolicitation(VirtualNetwork network, IP dstIp, MacAddress dstMac) {
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
        var routeRule = pkb.network.routeTable.lookup(dst);
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
            VirtualNetwork targetNetwork = swCtx.getNetwork(routeRule.toVni);
            if (targetNetwork == null) {
                assert Logger.lowLevelDebug("target vpc not found");
                return;
            }
            MacAddress targetMac = getRoutedSrcMac(targetNetwork, dst);
            if (targetMac == null) {
                assert Logger.lowLevelDebug("cannot find dst mac for sending this packet to another vpc");
                return;
            }
            pkb.pkt.setSrc(targetMac);
            pkb.pkt.setDst(targetMac);
            pkb.setNetwork(targetNetwork);
            L2.reinput(pkb);
        } else {
            assert Logger.lowLevelDebug("route based on ip");

            var targetIp = routeRule.ip;
            MacAddress dstMac = pkb.network.lookup(targetIp);
            if (dstMac == null) {
                assert Logger.lowLevelDebug("mac not found in arp table, run a broadcast");
                broadcastArpOrNdp(pkb.network, targetIp);
                return;
            }

            pkb.pkt.setDst(dstMac);
            directOutput(pkb);
        }
    }

    public void output(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("L3.output(" + pkb + ")");

        // assign mac to the packet
        MacAddress srcMac = pkb.network.ips.lookup(pkb.ipPkt.getSrc());
        if (srcMac == null) {
            Logger.shouldNotHappen("cannot find synthetic ip for sending the output packet");
            return;
        }
        MacAddress dstMac;

        // check routing rule
        var rule = pkb.network.routeTable.lookup(pkb.ipPkt.getDst());
        if (rule == null || rule.toVni == pkb.vni) {
            assert Logger.lowLevelDebug("no route rule or route to current network");

            // try to find the mac address of the dst
            dstMac = pkb.network.lookup(pkb.ipPkt.getDst());

            if (dstMac == null) {
                assert Logger.lowLevelDebug("cannot find dst mac for sending the packet");
                broadcastArpOrNdp(pkb.network, pkb.ipPkt.getDst());
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
