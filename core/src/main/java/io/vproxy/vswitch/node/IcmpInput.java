package io.vproxy.vswitch.node;

import io.vproxy.base.util.Consts;
import io.vproxy.base.util.Logger;
import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vpacket.*;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.util.SwitchUtils;

public class IcmpInput extends Node {
    private final NodeEgress ipOutputRoute = new NodeEgress("ip-output-route");
    private final NodeEgress icmpNeighborSolicitationInput = new NodeEgress("icmp-neighbor-solicitation-input");

    public IcmpInput() {
        super("icmp-input");
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        builder.addEdge("icmp-input", "ip-output-route", "ip-output-route", DEFAULT_EDGE_DISTANCE);
        builder.addEdge("icmp-input", "icmp-neighbor-solicitation-input", "icmp-neighbor-solicitation-input", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        fillEdges(ipOutputRoute);
        fillEdges(icmpNeighborSolicitationInput);
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return HandleResult.PASS;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        var ipPkt = pkb.ipPkt;
        // validate the icmp part
        var icmpPkt = (IcmpPacket) ipPkt.getPacket();
        if (ipPkt instanceof Ipv4Packet && icmpPkt.isIpv6()) {
            assert Logger.lowLevelDebug("drop the packet because it's icmpv6 inside ipv4");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("icmpv6 inside ipv4"));
            }
            return _returndrop(pkb);
        } else if (ipPkt instanceof Ipv6Packet && !icmpPkt.isIpv6()) {
            assert Logger.lowLevelDebug("drop the packet because it's icmpv4 inside ipv6");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("icmpv4 inside ipv6"));
            }
            return _returndrop(pkb);
        }

        if (icmpPkt.isIpv6()) {
            assert Logger.lowLevelDebug("is icmpv6");
            if (icmpPkt.getType() == Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Solicitation) {
                return _returnnext(pkb, icmpNeighborSolicitationInput);
            }

            return handleIcmpv6NotNDP(pkb);
        } else {
            return handleIcmpv4(pkb);
        }
    }

    private HandleResult handleIcmpv4(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("handleIcmpv4(" + pkb + ")");

        var ipPkt = (Ipv4Packet) pkb.pkt.getPacket();
        var icmp = (IcmpPacket) ipPkt.getPacket();

        if (icmp.getType() != Consts.ICMP_PROTOCOL_TYPE_ECHO_REQ) {
            assert Logger.lowLevelDebug("cannot handle this type icmp packet");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("cannot handle icmp type ").append(icmp.getType()));
            }
            return _returndrop(pkb);
        }

        return respondIcmpPong(pkb);
    }

    private HandleResult handleIcmpv6NotNDP(PacketBuffer pkb) {
        assert Logger.lowLevelDebug("handleIcmpv6(" + pkb + ")");

        var ipPkt = (Ipv6Packet) pkb.pkt.getPacket();
        var icmp = (IcmpPacket) ipPkt.getPacket();

        if (icmp.getType() != Consts.ICMPv6_PROTOCOL_TYPE_ECHO_REQ) {
            assert Logger.lowLevelDebug("cannot handle this type icmp packet");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("cannot handle icmp type ").append(icmp.getType()));
            }
            return _returndrop(pkb);
        }

        return respondIcmpPong(pkb);
    }

    private HandleResult respondIcmpPong(PacketBuffer pkb) {
        if (pkb.ensurePartialPacketParsed()) {
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("invalid packet"));
            }
            return _returndropSkipErrorDrop();
        }
        assert Logger.lowLevelDebug("respondIcmpPong(" + pkb + ")");

        var inIpPkt = (AbstractIpPacket) pkb.pkt.getPacket();
        var inIcmp = (IcmpPacket) inIpPkt.getPacket();

        var srcIp = inIpPkt.getDst();
        var srcMac = pkb.network.ips.lookup(srcIp);
        if (srcMac == null) {
            Logger.shouldNotHappen("cannot find src mac for sending the icmp echo resp packet");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("no src mac found for routing out the packet"));
            }
            return _returndrop(pkb);
        }

        IcmpPacket icmp = new IcmpPacket(inIpPkt instanceof Ipv6Packet);
        icmp.setType(inIcmp.isIpv6() ? Consts.ICMPv6_PROTOCOL_TYPE_ECHO_RESP : Consts.ICMP_PROTOCOL_TYPE_ECHO_RESP);
        icmp.setCode(0);
        icmp.setOther(inIcmp.getOther());

        EthernetPacket ether = SwitchUtils.buildEtherIpIcmpPacket(pkb.pkt.getSrc(), srcMac, srcIp, inIpPkt.getSrc(), icmp);
        pkb.replacePacket(ether);

        return _returnnext(pkb, ipOutputRoute);
    }
}
