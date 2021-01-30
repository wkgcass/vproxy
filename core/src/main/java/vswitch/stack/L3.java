package vswitch.stack;

import vfd.IP;
import vfd.IPv4;
import vfd.IPv6;
import vfd.MacAddress;
import vpacket.*;
import vproxybase.util.ByteArray;
import vproxybase.util.Consts;
import vproxybase.util.Logger;
import vswitch.IPMac;
import vswitch.Table;

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

    public void input(InputPacketL3Context ctx) {
        assert Logger.lowLevelDebug("L3.input(" + ctx + ")");
        if (ctx.isUnicast) {
            handleInputUnicast(ctx);
        } else {
            handleInputBroadcast(ctx);
        }
    }

    public void resolve(String handlingUUID, Table table, IP ip, MacAddress knownMac) {
        assert Logger.lowLevelDebug("lookupAddress(" + handlingUUID + "," + table + "," + ip + "," + knownMac + ")");
        if (ip instanceof IPv4) {
            arpResolve(handlingUUID, table, ip, knownMac);
        } else {
            ndpResolve(handlingUUID, table, ip, knownMac);
        }
    }

    private void arpResolve(String handlingUUID, Table table, IP ip, MacAddress knownMac) {
        assert Logger.lowLevelDebug("lookupAddress(" + handlingUUID + "," + table + "," + ip + "," + knownMac + ")");
        var iface = knownMac == null ? null : table.macTable.lookup(knownMac);
        if (iface == null) {
            assert Logger.lowLevelDebug(handlingUUID + " cannot find iface of the mac, try broadcast");
            broadcastArp(handlingUUID, table, ip);
        } else {
            assert Logger.lowLevelDebug(handlingUUID + " run unicast");
            unicastArp(handlingUUID, table, ip, knownMac);
        }
    }

    private void ndpResolve(String handlingUUID, Table table, IP ip, MacAddress knownMac) {
        assert Logger.lowLevelDebug("lookupAddress(" + handlingUUID + "," + table + "," + ip + "," + knownMac + ")");
        var iface = table.macTable.lookup(knownMac);
        if (iface == null) {
            assert Logger.lowLevelDebug(handlingUUID + " cannot find iface of the mac, try broadcast");
            broadcastNdp(handlingUUID, table, ip);
        } else {
            assert Logger.lowLevelDebug(handlingUUID + " run unicast");
            unicastNdp(handlingUUID, table, ip, knownMac);
        }
    }

    private void handleInputUnicast(InputPacketL3Context ctx) {
        assert Logger.lowLevelDebug("handleInputUnicast(" + ctx + ")");

        var packet = ctx.inputPacket.getPacket();
        if (packet instanceof ArpPacket) {
            handleArp(ctx);
            return;
        } else if (packet instanceof AbstractIpPacket) {
            handleUnicastIP(ctx);
            return;
        }
        assert Logger.lowLevelDebug(ctx.handlingUUID + " cannot process the packet");
    }

    private void handleInputBroadcast(InputPacketL3Context ctx) {
        assert Logger.lowLevelDebug("handleInputBroadcast(" + ctx + ")");

        var packet = ctx.inputPacket.getPacket();
        if (packet instanceof ArpPacket) {
            handleArp(ctx);
            return;
        } else if (packet instanceof AbstractIpPacket) {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " is ip packet");
            // we only handle icmpv6 packets when it's broadcasted

            var ipPkt = (AbstractIpPacket) packet;

            if (!(ipPkt instanceof Ipv6Packet)) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " is not ipv6. the packet protocol is " + ipPkt.getProtocol());
                return;
            }
            if (!(ipPkt.getPacket() instanceof IcmpPacket)) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " is not icmp");
                return;
            }
            var icmpPkt = (IcmpPacket) ipPkt.getPacket();
            if (!icmpPkt.isIpv6()) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " is not icmpv6");
                return;
            }
            if (icmpPkt.getType() != Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Solicitation) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " is not neighbor solicitation");
                return;
            }
            handleNeighborSolicitation(ctx);
            return;
        }
        assert Logger.lowLevelDebug(ctx.handlingUUID + " cannot process the packet");
    }

    private void handleArp(InputPacketL3Context ctx) {
        assert Logger.lowLevelDebug("handleArp(" + ctx + ")");

        ArpPacket arp = (ArpPacket) ctx.inputPacket.getPacket();
        if (arp.getProtocolType() != Consts.ARP_PROTOCOL_TYPE_IP) {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " type of arp packet is not ip");
            return;
        }
        assert Logger.lowLevelDebug(ctx.handlingUUID + " arp protocol is ip");
        if (arp.getOpcode() != Consts.ARP_PROTOCOL_OPCODE_REQ) {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " cannot handle this type arp message");
            return;
        }
        assert Logger.lowLevelDebug(ctx.handlingUUID + " arp is req");

        // only handle ipv4 in arp, v6 should be handled with ndp
        ByteArray targetIp = arp.getTargetIp();
        if (targetIp.length() != 4) {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " target ip length is not 4");
            return;
        }
        assert Logger.lowLevelDebug(ctx.handlingUUID + " arp target is ipv4");
        IP ip = IP.from(targetIp.toJavaArray());

        // check whether we can handle the packet
        if (!ctx.matchedIps.contains(ip)) {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " no matched ip found for the arp packet");
            return;
        }

        // handle
        MacAddress mac = ctx.table.ips.lookup(ip);

        assert Logger.lowLevelDebug(ctx.handlingUUID + " respond arp");
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
        ether.setDst(ctx.inputPacket.getSrc());
        ether.setSrc(mac);
        ether.setType(Consts.ETHER_TYPE_ARP);
        ether.setPacket(resp);

        directOutput(new OutputPacketL2Context(ctx.handlingUUID, ctx.table, ether));
    }

    private void handleUnicastIP(InputPacketL3Context ctx) {
        assert Logger.lowLevelDebug("handleUnicastIP(" + ctx + ")");

        // check whether we can handle the packet
        var ipPkt = (AbstractIpPacket) ctx.inputPacket.getPacket();

        // check whether we can handle the packet
        var dst = ipPkt.getDst();
        if (!ctx.matchedIps.contains(dst)) {
            route(ctx);
            return;
        }
        assert Logger.lowLevelDebug(ctx.handlingUUID + " no need to route the packet");

        if (ipPkt.getPacket() instanceof IcmpPacket) {
            // validate the icmp part
            var icmpPkt = (IcmpPacket) ipPkt.getPacket();
            if (ipPkt instanceof Ipv4Packet && icmpPkt.isIpv6()) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " drop the packet because it's icmpv6 inside ipv4");
                return;
            } else if (ipPkt instanceof Ipv6Packet && !icmpPkt.isIpv6()) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " drop the packet because it's icmpv4 inside ipv6");
                return;
            }

            if (icmpPkt.isIpv6()) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " is icmpv6");
                if (icmpPkt.getType() == Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Solicitation ||
                    icmpPkt.getType() == Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Advertisement) {
                    handleNDP(ctx);
                    return;
                }
                assert Logger.lowLevelDebug(ctx.handlingUUID + " is not NDP");

                handleIcmpv6NotNDP(ctx);
                return;
            } else {
                handleIcmpv4(ctx);
                return;
            }
        } else if (ipPkt.getProtocol() == Consts.IP_PROTOCOL_TCP || ipPkt.getProtocol() == Consts.IP_PROTOCOL_UDP || ipPkt.getProtocol() == Consts.IP_PROTOCOL_SCTP) {
            var l4ctx = new InputPacketL4Context(ctx);
            if (L4.input(l4ctx)) {
                return;
            }
            respondIcmpPortUnreachable(ctx);
            return;
        } // else other packet types may be supported in the future
        assert Logger.lowLevelDebug(ctx.handlingUUID + " nothing to be done for the packet");
    }

    private void handleIcmpv4(InputPacketL3Context ctx) {
        assert Logger.lowLevelDebug("handleIcmpv4(" + ctx + ")");

        var ipPkt = (Ipv4Packet) ctx.inputPacket.getPacket();
        var icmp = (IcmpPacket) ipPkt.getPacket();

        if (icmp.getType() != Consts.ICMP_PROTOCOL_TYPE_ECHO_REQ) {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " cannot handle this type icmp packet");
            return;
        }

        respondIcmpPong(ctx);
    }

    private void handleIcmpv6NotNDP(InputPacketL3Context ctx) {
        assert Logger.lowLevelDebug("handleIcmpv6(" + ctx + ")");

        var ipPkt = (Ipv6Packet) ctx.inputPacket.getPacket();
        var icmp = (IcmpPacket) ipPkt.getPacket();

        if (icmp.getType() != Consts.ICMPv6_PROTOCOL_TYPE_ECHO_REQ) {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " cannot handle this type icmp packet");
            return;
        }

        respondIcmpPong(ctx);
    }

    private void handleNDP(InputPacketL3Context ctx) {
        assert Logger.lowLevelDebug("handleNDP(" + ctx + ")");
        var icmpv6 = (IcmpPacket) (((Ipv6Packet) ctx.inputPacket.getPacket()).getPacket());
        if (icmpv6.getType() == Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Solicitation) {
            handleNeighborSolicitation(ctx);
            return;
        }
        assert Logger.lowLevelDebug(ctx.handlingUUID + " cannot handle the ndp packet");
    }

    private void handleNeighborSolicitation(InputPacketL3Context ctx) {
        assert Logger.lowLevelDebug("handleNeighborSolicitation(" + ctx + ")");

        var inIpPkt = (Ipv6Packet) ctx.inputPacket.getPacket();
        var inIcmp = (IcmpPacket) inIpPkt.getPacket();

        ByteArray other = inIcmp.getOther();
        if (other.length() < 20) { // 4 reserved and 16 target address
            assert Logger.lowLevelDebug(ctx.handlingUUID + " invalid packet for neighbor solicitation: too short");
            return;
        }
        assert Logger.lowLevelDebug(ctx.handlingUUID + " is a valid neighbor solicitation");

        byte[] targetAddr = other.sub(4, 16).toJavaArray();
        IPv6 ndpNeighborSolicitation = IP.fromIPv6(targetAddr);

        if (!ctx.matchedIps.contains(ndpNeighborSolicitation)) {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " this ndp ns does not request for matched synthetic ip");
            return;
        }

        MacAddress correspondingMac = ctx.table.ips.lookup(ndpNeighborSolicitation);
        if (correspondingMac == null) {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " requested ip is not synthetic ip");
            return;
        }

        respondIcmpNeighborAdvertisement(ctx, ndpNeighborSolicitation, correspondingMac);
    }

    private void respondIcmpNeighborAdvertisement(InputPacketL3Context ctx, IPv6 requestedIp, MacAddress requestedMac) {
        assert Logger.lowLevelDebug("respondIcmpNeighborAdvertisement(" + ctx + "," + requestedIp + "," + requestedMac + ")");

        var inIpPkt = (Ipv6Packet) ctx.inputPacket.getPacket();

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
        ether.setDst(ctx.inputPacket.getSrc());
        ether.setSrc(requestedMac);
        ether.setType(Consts.ETHER_TYPE_IPv6);
        ether.setPacket(ipv6);

        directOutput(new OutputPacketL2Context(ctx.handlingUUID, ctx.table, ether));
    }

    private void respondIcmpPong(InputPacketL3Context ctx) {
        assert Logger.lowLevelDebug("respondIcmpPong(" + ctx + ")");

        var inIpPkt = (AbstractIpPacket) ctx.inputPacket.getPacket();
        var inIcmp = (IcmpPacket) inIpPkt.getPacket();

        var srcIp = inIpPkt.getDst();
        var srcMac = ctx.table.ips.lookup(srcIp);
        if (srcMac == null) {
            Logger.shouldNotHappen(ctx.handlingUUID + " cannot find src mac for sending the icmp echo resp packet");
            return;
        }

        IcmpPacket icmp = new IcmpPacket(inIpPkt instanceof Ipv6Packet);
        icmp.setType(inIcmp.isIpv6() ? Consts.ICMPv6_PROTOCOL_TYPE_ECHO_RESP : Consts.ICMP_PROTOCOL_TYPE_ECHO_RESP);
        icmp.setCode(0);
        icmp.setOther(inIcmp.getOther());

        EthernetPacket ether = buildEtherIpIcmpPacket(ctx.handlingUUID, ctx.inputPacket.getSrc(), srcMac, srcIp, inIpPkt.getSrc(), icmp);

        routeOutput(new OutputPacketL2Context(ctx.handlingUUID, ctx.table, ether));
    }

    private void respondIcmpPortUnreachable(InputPacketL3Context ctx) {
        assert Logger.lowLevelDebug("respondIcmpPortUnreachable(" + ctx + ")");

        var inIpPkt = (AbstractIpPacket) ctx.inputPacket.getPacket();

        boolean isIpv6 = inIpPkt instanceof Ipv6Packet;
        var srcIp = inIpPkt.getDst();
        var srcMac = ctx.table.ips.lookup(srcIp);
        if (srcMac == null) {
            Logger.shouldNotHappen(ctx.handlingUUID + " cannot find src mac for sending the icmp time exceeded packet");
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

        EthernetPacket ether = buildEtherIpIcmpPacket(ctx.handlingUUID, ctx.inputPacket.getSrc(), srcMac, srcIp, inIpPkt.getSrc(), icmp);

        routeOutput(new OutputPacketL2Context(ctx.handlingUUID, ctx.table, ether));
    }

    private void respondIcmpTimeExceeded(InputPacketL3Context ctx) {
        assert Logger.lowLevelDebug("respondIcmpTimeExceeded(" + ctx + ")");

        var inIpPkt = (AbstractIpPacket) ctx.inputPacket.getPacket();
        boolean isIpv6 = inIpPkt instanceof Ipv6Packet;
        var srcIpAndMac = getRoutedSrcIpAndMac(ctx.handlingUUID, ctx.table, inIpPkt.getSrc());
        if (srcIpAndMac == null) {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " cannot find src ip for sending the icmp time exceeded packet");
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

        EthernetPacket ether = buildEtherIpIcmpPacket(ctx.handlingUUID, ctx.inputPacket.getSrc(), srcIpAndMac.mac, srcIpAndMac.ip, inIpPkt.getSrc(), icmp);

        routeOutput(new OutputPacketL2Context(ctx.handlingUUID, ctx.table, ether));
    }

    private void route(InputPacketL3Context ctx) {
        assert Logger.lowLevelDebug("route(" + ctx + ")");

        var ippkt = (AbstractIpPacket) ctx.inputPacket.getPacket();
        var dst = ippkt.getDst();

        // reduce ip packet hop
        {
            int hop = ippkt.getHopLimit();
            if (hop <= 1) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " hop too low, drop");
                respondIcmpTimeExceeded(ctx);
                return;
            }
            hop -= 1;
            ippkt.setHopLimit(hop);
            ctx.inputPacket.clearRawPacket();
            ctx.clearVXLanRawPacket();
        }

        // find ruling rule for the dst
        var rule = ctx.table.routeTable.lookup(dst);
        if (rule == null) {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " no route rule found");
            return;
        }
        assert Logger.lowLevelDebug(ctx.handlingUUID + " route rule found");

        int vni = rule.toVni;
        if (vni == ctx.table.vni) {
            // direct route
            assert Logger.lowLevelDebug(ctx.handlingUUID + " in the same vpc");

            MacAddress dstMac = ctx.table.lookup(dst);
            if (dstMac == null) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " cannot find correct mac");
                broadcastArpOrNdp(ctx.handlingUUID, ctx.table, dst);
                return;
            }
            assert Logger.lowLevelDebug(ctx.handlingUUID + " found the correct mac");

            var srcMac = getRoutedSrcMac(ctx.handlingUUID, ctx.table, dst);
            if (srcMac == null) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " cannot route because src mac is not found");
                return;
            }

            ctx.inputPacket.setSrc(srcMac);
            ctx.inputPacket.setDst(dstMac);
            directOutput(new OutputPacketL2Context(ctx.handlingUUID, ctx.table, ctx.inputPacket));
        } else if (vni != 0) {
            // route to another network
            assert Logger.lowLevelDebug(ctx.handlingUUID + " routing to another vpc: " + vni);
            Table t = swCtx.getTable(vni);
            if (t == null) { // cannot handle if the table does no exist
                assert Logger.lowLevelDebug(ctx.handlingUUID + " target table " + vni + " is not found");
                return;
            }
            assert Logger.lowLevelDebug(ctx.handlingUUID + " target table is found");

            // get target mac
            var targetMac = getRoutedSrcMac(ctx.handlingUUID, t, dst);
            if (targetMac == null) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " cannot route because target mac is not found");
                return;
            }

            ctx.inputPacket.setSrc(targetMac);
            ctx.inputPacket.setDst(targetMac);
            if (ctx.inputVXLan != null) {
                ctx.inputVXLan.setVni(t.vni);
            }
            L2.input(new InputPacketL2Context(ctx.handlingUUID, null, t, ctx.inputVXLan, ctx.inputPacket));
        } else {
            // route based on ip
            var targetIp = rule.ip;
            assert Logger.lowLevelDebug(ctx.handlingUUID + " gateway rule");
            MacAddress dstMac = ctx.table.lookup(targetIp);
            if (dstMac == null) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " mac not found in arp table, run a broadcast");
                broadcastArpOrNdp(ctx.handlingUUID, ctx.table, targetIp);
                return;
            }

            var srcMac = getRoutedSrcMac(ctx.handlingUUID, ctx.table, dst);
            if (srcMac == null) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " cannot route because src mac is not found");
                return;
            }

            ctx.inputPacket.setSrc(srcMac);
            ctx.inputPacket.setDst(dstMac);
            directOutput(new OutputPacketL2Context(ctx.handlingUUID, ctx.table, ctx.inputPacket));
        }
    }

    private void broadcastArpOrNdp(String handlingUUID, Table table, IP dst) {
        assert Logger.lowLevelDebug("getRoutedSrcMac(" + handlingUUID + "," + table + "," + dst + ")");
        if (dst instanceof IPv4) {
            broadcastArp(handlingUUID, table, dst);
        } else {
            broadcastNdp(handlingUUID, table, dst);
        }
    }

    private void broadcastArp(String handlingUUID, Table table, IP dst) {
        assert Logger.lowLevelDebug("broadcastArp(" + handlingUUID + "," + table + "," + dst + ")");

        EthernetPacket packet = buildArpReq(handlingUUID, table, dst, new MacAddress("ff:ff:ff:ff:ff:ff"));
        if (packet == null) {
            assert Logger.lowLevelDebug(handlingUUID + " failed to build arp packet");
            return;
        }
        directOutput(new OutputPacketL2Context(handlingUUID, table, packet));
    }

    private void unicastArp(String handlingUUID, Table table, IP dst, MacAddress dstMac) {
        assert Logger.lowLevelDebug("unicastArp(" + handlingUUID + "," + table + "," + dst + "," + dstMac + ")");

        EthernetPacket packet = buildArpReq(handlingUUID, table, dst, dstMac);
        if (packet == null) {
            assert Logger.lowLevelDebug(handlingUUID + " failed to build arp packet");
            return;
        }
        directOutput(new OutputPacketL2Context(handlingUUID, table, packet));
    }

    private void broadcastNdp(String handlingUUID, Table table, IP dst) {
        assert Logger.lowLevelDebug("broadcastNdp(" + handlingUUID + "," + table + "," + dst + ")");

        EthernetPacket packet = buildNdpNeighborSolicitation(handlingUUID, table, dst, new MacAddress("ff:ff:ff:ff:ff:ff"));
        if (packet == null) {
            assert Logger.lowLevelDebug(handlingUUID + " failed to build ndp neighbor solicitation packet");
            return;
        }
        directOutput(new OutputPacketL2Context(handlingUUID, table, packet));
    }

    private void unicastNdp(String handlingUUID, Table table, IP dst, MacAddress dstMac) {
        assert Logger.lowLevelDebug("unicastNdp(" + handlingUUID + "," + table + "," + dst + "," + dstMac + ")");

        EthernetPacket packet = buildNdpNeighborSolicitation(handlingUUID, table, dst, dstMac);
        if (packet == null) {
            assert Logger.lowLevelDebug(handlingUUID + " failed to build ndp neighbor solicitation packet");
            return;
        }

        directOutput(new OutputPacketL2Context(handlingUUID, table, packet));
    }

    private IPMac getRoutedSrcIpAndMac(String handlingUUID, Table table, IP dstIp) {
        assert Logger.lowLevelDebug("getRoutedSrcIpAndMac(" + handlingUUID + ", " + table + "," + dstIp + ")");

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

    private MacAddress getRoutedSrcMac(String handlingUUID, Table table, IP dstIp) {
        assert Logger.lowLevelDebug("getRoutedSrcMac(" + handlingUUID + "," + table + "," + dstIp + ")");
        var entry = getRoutedSrcIpAndMac(handlingUUID, table, dstIp);
        if (entry == null) {
            return null;
        }
        return entry.mac;
    }

    private EthernetPacket buildEtherIpIcmpPacket(String handlingUUID, MacAddress dstMac, MacAddress srcMac, IP srcIp, IP dstIp, IcmpPacket icmp) {
        assert Logger.lowLevelDebug("buildIpIcmpPacket(" + handlingUUID + "," + dstMac + "," + srcMac + srcIp + "," + dstIp + icmp + ")");

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

    private EthernetPacket buildArpReq(String handlingUUID, Table table, IP dstIp, MacAddress dstMac) {
        assert Logger.lowLevelDebug("buildArpReq(" + handlingUUID + "," + table + "," + dstIp + "," + dstMac + ")");

        var optIp = table.ips.entries().stream().filter(x -> x.ip instanceof IPv4).findAny();
        if (optIp.isEmpty()) {
            assert Logger.lowLevelDebug(handlingUUID + " cannot find synthetic ipv4 in the table");
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

    private EthernetPacket buildNdpNeighborSolicitation(String handlingUUID, Table table, IP dstIp, MacAddress dstMac) {
        assert Logger.lowLevelDebug("buildNdpNeighborSolicitation(" + handlingUUID + "," + table + "," + dstIp + "," + dstMac + ")");

        var optIp = table.ips.entries().stream().filter(x -> x.ip instanceof IPv6).findAny();
        if (optIp.isEmpty()) {
            assert Logger.lowLevelDebug(handlingUUID + " cannot find synthetic ipv4 in the table");
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

    private void directOutput(OutputPacketL2Context ctx) {
        assert Logger.lowLevelDebug("directOutput(" + ctx + ")");
        L2.output(ctx);
    }

    private void routeOutput(OutputPacketL2Context ctx) {
        assert Logger.lowLevelDebug("routeOutput(" + ctx + ")");

        if (!ctx.outputPacket.getDst().isUnicast()) {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " packet is not unicast, no need to route");
            directOutput(ctx);
            return;
        }

        var ipPkt = (AbstractIpPacket) ctx.outputPacket.getPacket();
        var dst = ipPkt.getDst();
        var routeRule = ctx.table.routeTable.lookup(dst);
        if (routeRule == null) {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " no route rule found for the ip dst, no need to route");
            directOutput(ctx);
            return;
        }

        if (routeRule.toVni == ctx.table.vni) {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " direct route, no changes required");
            directOutput(ctx);
        } else if (routeRule.toVni != 0) {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " route to another vpc");

            // search for any synthetic ip in the target vpc
            Table targetTable = swCtx.getTable(routeRule.toVni);
            if (targetTable == null) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " target vpc not found");
                return;
            }
            MacAddress targetMac = getRoutedSrcMac(ctx.handlingUUID, targetTable, dst);
            if (targetMac == null) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " cannot find dst mac for sending this packet to another vpc");
                return;
            }
            ctx.outputPacket.setSrc(targetMac);
            ctx.outputPacket.setDst(targetMac);
            L2.input(new InputPacketL2Context(ctx.handlingUUID, null, targetTable, ctx.outputPacket));
        } else {
            assert Logger.lowLevelDebug(ctx.handlingUUID + " route based on ip");

            var targetIp = routeRule.ip;
            MacAddress dstMac = ctx.table.lookup(targetIp);
            if (dstMac == null) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " mac not found in arp table, run a broadcast");
                broadcastArpOrNdp(ctx.handlingUUID, ctx.table, targetIp);
                return;
            }

            ctx.outputPacket.setDst(dstMac);
            directOutput(ctx);
        }
    }

    public void output(OutputPacketL3Context ctx) {
        assert Logger.lowLevelDebug("L3.output(" + ctx + ")");

        // assign mac to the packet
        MacAddress srcMac = ctx.table.ips.lookup(ctx.outputPacket.getSrc());
        if (srcMac == null) {
            Logger.shouldNotHappen("cannot find synthetic ip for sending the output packet");
            return;
        }
        MacAddress dstMac;

        // check routing rule
        var rule = ctx.table.routeTable.lookup(ctx.outputPacket.getDst());
        if (rule == null || rule.toVni == ctx.table.vni) {
            assert Logger.lowLevelDebug("no route rule or route to current network");

            // try to find the mac address of the dst
            dstMac = ctx.table.lookup(ctx.outputPacket.getDst());

            if (dstMac == null) {
                assert Logger.lowLevelDebug(ctx.handlingUUID + " cannot find dst mac for sending the packet");
                broadcastArpOrNdp(ctx.handlingUUID, ctx.table, ctx.outputPacket.getDst());
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
        ether.setType((ctx.outputPacket instanceof Ipv4Packet) ? Consts.ETHER_TYPE_IPv4 : Consts.ETHER_TYPE_IPv6);
        ether.setPacket(ctx.outputPacket);

        // route out
        routeOutput(new OutputPacketL2Context(ctx.handlingUUID, ctx.table, ether));
    }
}
