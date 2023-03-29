package io.vproxy.vswitch.node;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.base.util.Logger;
import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vpacket.*;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.SwitchDelegate;
import io.vproxy.vswitch.VirtualNetwork;
import io.vproxy.vswitch.util.SwitchUtils;

public class IPInputRoute extends AbstractNeighborResolve {
    private final SwitchDelegate sw;
    private final NodeEgress ipInput = new NodeEgress("ip-input");
    private final NodeEgress ethernetOutput = new NodeEgress("ethernet-output");
    private final NodeEgress ethernetReinput = new NodeEgress("ethernet-reinput");
    private final NodeEgress ipOutputRoute = new NodeEgress("ip-output-route");

    public IPInputRoute(SwitchDelegate sw) {
        super("ip-input-route");
        this.sw = sw;
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        super.initGraph(builder);
        builder.addEdge("ip-input-route", "ip-input", "ip-input", DEFAULT_EDGE_DISTANCE);
        builder.addEdge("ip-input-route", "ethernet-output", "ethernet-output", DEFAULT_EDGE_DISTANCE);
        builder.addEdge("ip-input-route", "ethernet-reinput", "ethernet-reinput", DEFAULT_EDGE_DISTANCE);
        builder.addEdge("ip-input-route", "ip-output-route", "ip-output-route", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        super.initNode();
        fillEdges(ipInput);
        fillEdges(ethernetOutput);
        fillEdges(ethernetReinput);
        fillEdges(ipOutputRoute);
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return HandleResult.PASS;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        // check local
        if (pkb.matchedIps.contains(pkb.ipPkt.getDst())) {
            return _next(pkb, ipInput);
        }
        // route
        return route(pkb);
    }

    private HandleResult route(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("route(" + pkb + ")");

        var ippkt = (AbstractIpPacket) pkb.pkt.getPacket();

        if (ippkt.getPacket() instanceof IcmpPacket &&
            (
                ((IcmpPacket) ippkt.getPacket()).getType() == Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Solicitation ||
                    ((IcmpPacket) ippkt.getPacket()).getType() == Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Advertisement
            )
        ) {
            assert Logger.lowLevelDebug("ndp ns/na cannot be routed, drop this packet");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("is ndp ns/na"));
            }
            return _returndrop(pkb);
        }

        var dst = ippkt.getDst();

        // reduce ip packet hop
        {
            int hop = ippkt.getHopLimit();
            if (hop <= 1) {
                assert Logger.lowLevelDebug("hop too low, drop");
                if (pkb.debugger.isDebugOn()) {
                    pkb.debugger.line(d -> d.append("ttl/hop-limit <= 1"));
                }
                return respondIcmpTimeExceeded(pkb);
            }
            hop -= 1;
            ippkt.setHopLimit(hop);
        }

        // find routing rule for the dst
        var rule = pkb.network.routeTable.lookup(dst);
        if (rule == null) {
            assert Logger.lowLevelDebug("no route rule found");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("no route"));
            }
            return _returndrop(pkb);
        }
        assert Logger.lowLevelDebug("route rule found: " + rule);

        int vni = rule.toVni;
        if (vni == pkb.vni) {
            // direct route
            assert Logger.lowLevelDebug("in the same vpc");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("direct route"));
            }

            MacAddress dstMac = pkb.network.lookup(dst);
            if (dstMac == null) {
                assert Logger.lowLevelDebug("cannot find correct mac");
                if (pkb.debugger.isDebugOn()) {
                    pkb.debugger.line(d -> d.append("cannot find dst mac"));
                }
                return broadcastArpOrNdp(pkb.network, dst, pkb);
            }
            assert Logger.lowLevelDebug("found the correct mac");

            var srcMac = SwitchUtils.getRoutedSrcMac(pkb.network, dst);
            if (srcMac == null) {
                assert Logger.lowLevelDebug("cannot route because src mac is not found");
                if (pkb.debugger.isDebugOn()) {
                    pkb.debugger.line(d -> d.append("no src mac found for routing out the packet"));
                }
                return _returndrop(pkb);
            }

            pkb.pkt.setSrc(srcMac);
            pkb.pkt.setDst(dstMac);
            return _returnnext(pkb, ethernetOutput);
        } else if (vni != 0) {
            // route to another network
            assert Logger.lowLevelDebug("routing to another vpc: " + vni);
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("route to another network"));
            }

            VirtualNetwork n = sw.getNetwork(vni);
            if (n == null) { // cannot handle if the network does no exist
                assert Logger.lowLevelDebug("target network " + vni + " is not found");
                if (pkb.debugger.isDebugOn()) {
                    pkb.debugger.line(d -> d.append("target network not found"));
                }
                return _returndrop(pkb);
            }
            assert Logger.lowLevelDebug("target network is found");

            // get src mac
            var newSrcMac = SwitchUtils.getRoutedSrcMac(n, dst);
            if (newSrcMac == null) {
                assert Logger.lowLevelDebug("cannot route because source mac is not found");
                if (pkb.debugger.isDebugOn()) {
                    pkb.debugger.line(d -> d.append("no src mac found for routing out the packet"));
                }
                return _returndrop(pkb);
            }

            var targetRule = n.routeTable.lookup(dst);
            if (targetRule != null && !targetRule.isLocalDirect(n.vni)) {
                assert Logger.lowLevelDebug("still require routing after switching the network");
                if (pkb.debugger.isDebugOn()) {
                    pkb.debugger.line(d -> d.append("still need routing after switching the network"));
                }

                pkb.setNetwork(n);
                pkb.pkt.setSrc(newSrcMac);
                return _returnnext(pkb, ipOutputRoute);
            }

            assert Logger.lowLevelDebug("direct route after switching the network");

            // get target mac
            var targetMac = n.lookup(dst);
            if (targetMac == null) {
                assert Logger.lowLevelDebug("cannot route because dest mac is not found");
                if (pkb.debugger.isDebugOn()) {
                    pkb.debugger.line(d -> d.append("cannot find dst mac"));
                }
                return broadcastArpOrNdp(n, dst, pkb);
            }
            assert Logger.lowLevelDebug("found dst mac: " + targetMac);

            pkb.pkt.setSrc(newSrcMac);
            pkb.pkt.setDst(targetMac);
            pkb.setNetwork(n);
            return _returnnext(pkb, ethernetReinput);
        } else {
            // route based on ip
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("route via ip"));
            }

            var targetIp = rule.ip;
            assert Logger.lowLevelDebug("gateway rule");
            MacAddress dstMac = pkb.network.lookup(targetIp);
            if (dstMac == null) {
                assert Logger.lowLevelDebug("mac not found in arp table, run a broadcast");
                if (pkb.debugger.isDebugOn()) {
                    pkb.debugger.line(d -> d.append("gateway mac not found"));
                }
                return broadcastArpOrNdp(pkb.network, targetIp, pkb);
            }

            var srcMac = SwitchUtils.getRoutedSrcMac(pkb.network, dst);
            if (srcMac == null) {
                assert Logger.lowLevelDebug("cannot route because src mac is not found");
                if (pkb.debugger.isDebugOn()) {
                    pkb.debugger.line(d -> d.append("no src mac found for routing out the packet"));
                }
                return _returndrop(pkb);
            }

            pkb.pkt.setSrc(srcMac);
            pkb.pkt.setDst(dstMac);
            return _returnnext(pkb, ethernetOutput);
        }
    }

    private HandleResult respondIcmpTimeExceeded(PacketBuffer pkb) {
        if (pkb.ensurePartialPacketParsed()) {
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.append("invalid packet");
            }
            return _returndropSkipErrorDrop();
        }
        assert Logger.lowLevelDebug("respondIcmpTimeExceeded(" + pkb + ")");

        var inIpPkt = (AbstractIpPacket) pkb.pkt.getPacket();
        boolean isIpv6 = inIpPkt instanceof Ipv6Packet;
        var srcIpAndMac = SwitchUtils.getRoutedSrcIpAndMac(pkb.network, inIpPkt.getSrc());
        if (srcIpAndMac == null) {
            assert Logger.lowLevelDebug("cannot find src ip for sending the icmp time exceeded packet");
            return _returndrop(pkb);
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

        EthernetPacket ether = SwitchUtils.buildEtherIpIcmpPacket(pkb.pkt.getSrc(), srcIpAndMac.mac, srcIpAndMac.ip, inIpPkt.getSrc(), icmp);
        pkb.replacePacket(ether);
        pkb.devin = null;

        return _returnnext(pkb, ipOutputRoute);
    }
}
