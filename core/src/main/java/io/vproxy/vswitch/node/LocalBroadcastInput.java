package io.vproxy.vswitch.node;

import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vpacket.AbstractIpPacket;
import io.vproxy.vpacket.ArpPacket;
import io.vproxy.vswitch.PacketBuffer;

public class LocalBroadcastInput extends Node {
    private final NodeEgress arpBroadcastInput = new NodeEgress("arp-broadcast-input");
    private final NodeEgress ipBroadcastInput = new NodeEgress("ip-broadcast-input");

    public LocalBroadcastInput() {
        super("local-broadcast-input");
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        builder.addEdge("local-broadcast-input", "arp-broadcast-input", "arp-broadcast-input", DEFAULT_EDGE_DISTANCE);
        builder.addEdge("local-broadcast-input", "ip-broadcast-input", "ip-broadcast-input", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        fillEdges(arpBroadcastInput);
        fillEdges(ipBroadcastInput);
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return HandleResult.PASS;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        pkb.setMatchedIps(pkb.network.ips.allIps());
        var packet = pkb.pkt.getPacket();
        if (packet instanceof ArpPacket) {
            return _returnnext(pkb, arpBroadcastInput);
        } else if (packet instanceof AbstractIpPacket) {
            return _returnnext(pkb, ipBroadcastInput);
        } else {
            return _returndrop(pkb);
        }
    }
}
