package io.vproxy.vswitch.node;

import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vpacket.IcmpPacket;
import io.vproxy.vswitch.PacketBuffer;

public class IPBroadcastInput extends Node {
    private final NodeEgress icmpBroadcastInput = new NodeEgress("icmp-broadcast-input");

    public IPBroadcastInput() {
        super("ip-broadcast-input");
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        builder.addEdge("ip-broadcast-input", "icmp-broadcast-input", "icmp-broadcast-input", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        fillEdges(icmpBroadcastInput);
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return HandleResult.PASS;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        var ipPkt = pkb.ipPkt;

        if (ipPkt.getPacket() instanceof IcmpPacket) {
            return _returnnext(pkb, icmpBroadcastInput);
        }
        return _returndrop(pkb);
    }
}
