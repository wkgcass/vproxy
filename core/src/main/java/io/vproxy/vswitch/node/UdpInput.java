package io.vproxy.vswitch.node;

import io.vproxy.base.Config;
import io.vproxy.base.util.Logger;
import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vfd.IPPort;
import io.vproxy.vpacket.UdpPacket;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.stack.conntrack.EnhancedUDPEntry;

public class UdpInput extends Node {
    private final NodeEgress icmpPortUnreachableOutput = new NodeEgress("icmp-port-unreachable-output");

    public UdpInput() {
        super("udp-input");
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        builder.addEdge("udp-input", "icmp-port-unreachable-output", "icmp-port-unreachable-output", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        fillEdges(icmpPortUnreachableOutput);
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return HandleResult.PASS;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        var ipPkt = pkb.ipPkt;
        var udpPkt = (UdpPacket) ipPkt.getPacket();
        IPPort dst = new IPPort(ipPkt.getDst(), udpPkt.getDstPort());
        var udpListen = pkb.network.conntrack.lookupUdpListen(dst);
        if (udpListen == null) {
            return _returnnext(pkb, icmpPortUnreachableOutput);
        }

        assert Logger.lowLevelDebug("handleUdp(" + pkb + ")");
        boolean ok = udpListen.receivingQueue.store(pkb.ipPkt.getSrc(), pkb.udpPkt.getSrcPort(), pkb.udpPkt.getData().getRawPacket(0));
        if (!ok) {
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("packet not accepted"));
            }
            return _returndrop(pkb);
        }
        assert Logger.lowLevelDebug("recording udp entry: " + pkb);
        var remote = new IPPort(pkb.ipPkt.getSrc(), pkb.udpPkt.getSrcPort());
        var local = new IPPort(pkb.ipPkt.getDst(), pkb.udpPkt.getDstPort());
        pkb.udp = pkb.network.conntrack.recordUdp(remote, local, () -> {
            var entry = new EnhancedUDPEntry(udpListen, remote, local, pkb.network.conntrack, Config.udpTimeout);
            entry.userData = pkb.fastpathUserData;
            return entry;
        });
        return _return(HandleResult.STOLEN, pkb);
    }
}
