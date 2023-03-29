package io.vproxy.vswitch.node;

import io.vproxy.base.util.Consts;
import io.vproxy.base.util.Logger;
import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vpacket.IcmpPacket;
import io.vproxy.vpacket.Ipv6Packet;
import io.vproxy.vswitch.PacketBuffer;

public class IcmpBroadcastInput extends Node {
    private final NodeEgress icmpNeighborSolicitationInput = new NodeEgress("icmp-neighbor-solicitation-input");

    public IcmpBroadcastInput() {
        super("icmp-broadcast-input");
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        builder.addEdge("icmp-broadcast-input", "icmp-neighbor-solicitation-input", "icmp-neighbor-solicitation-input", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        fillEdges(icmpNeighborSolicitationInput);
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return HandleResult.PASS;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        // we only handle icmpv6 neighbor solicitation when it's broadcast

        var ipPkt = pkb.ipPkt;
        if (!(ipPkt instanceof Ipv6Packet)) {
            assert Logger.lowLevelDebug("is not ipv6. the packet protocol is " + ipPkt.getProtocol());
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.append("is not ipv6");
            }
            return _returndrop(pkb);
        }
        if (!(ipPkt.getPacket() instanceof IcmpPacket)) {
            assert Logger.lowLevelDebug("is not icmp");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.append("is not icmp");
            }
            return _returndrop(pkb);
        }
        var icmpPkt = (IcmpPacket) ipPkt.getPacket();
        if (!icmpPkt.isIpv6()) {
            assert Logger.lowLevelDebug("is not icmpv6");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.append("is not icmpv6");
            }
            return _returndrop(pkb);
        }
        if (icmpPkt.getType() != Consts.ICMPv6_PROTOCOL_TYPE_Neighbor_Solicitation) {
            assert Logger.lowLevelDebug("is not neighbor solicitation");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.append("is not neighbor solicitation");
            }
            return _returndrop(pkb);
        }
        return _returnnext(pkb, icmpNeighborSolicitationInput);
    }
}
