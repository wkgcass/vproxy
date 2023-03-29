package io.vproxy.vswitch.node;

import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vpacket.IcmpPacket;
import io.vproxy.vpacket.TcpPacket;
import io.vproxy.vpacket.UdpPacket;
import io.vproxy.vswitch.PacketBuffer;

public class IPInput extends Node {
    private final NodeEgress icmpInput = new NodeEgress("icmp-input");
    private final NodeEgress tcpInput = new NodeEgress("tcp-input");
    private final NodeEgress udpInput = new NodeEgress("udp-input");

    public IPInput() {
        super("ip-input");
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        builder.addEdge("ip-input", "icmp-input", "icmp-input", DEFAULT_EDGE_DISTANCE);
        builder.addEdge("ip-input", "tcp-input", "tcp-input", DEFAULT_EDGE_DISTANCE);
        builder.addEdge("ip-input", "udp-input", "udp-input", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        fillEdges(icmpInput);
        fillEdges(tcpInput);
        fillEdges(udpInput);
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return HandleResult.PASS;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        var pkt = pkb.ipPkt.getPacket();
        if (pkt instanceof TcpPacket) {
            return _returnnext(pkb, tcpInput);
        } else if (pkt instanceof UdpPacket) {
            return _returnnext(pkb, udpInput);
        } else if (pkt instanceof IcmpPacket) {
            return _returnnext(pkb, icmpInput);
        } else {
            return _returndrop(pkb);
        }
    }
}
