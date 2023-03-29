package io.vproxy.vswitch.node;

import io.vproxy.base.util.Logger;
import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vpacket.AbstractIpPacket;
import io.vproxy.vpacket.ArpPacket;
import io.vproxy.vswitch.PacketBuffer;

public class LocalUnicastInput extends Node {
    private final NodeEgress arpInput = new NodeEgress("arp-input");
    private final NodeEgress ipRoute = new NodeEgress("ip-input-route");

    public LocalUnicastInput() {
        super("local-unicast-input");
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        builder.addEdge("local-unicast-input", "arp-input", "arp-input", DEFAULT_EDGE_DISTANCE);
        builder.addEdge("local-unicast-input", "ip-input-route", "ip-input-route", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        fillEdges(arpInput);
        fillEdges(ipRoute);
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        // then we search whether we have virtual hosts can accept the packet
        var dst = pkb.pkt.getDst();
        var ips = pkb.network.ips.lookupByMac(dst);
        if (ips == null) {
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger
                    .append("mac ").append(dst).append(" is not local")
                    .newLine();
            }
            assert Logger.lowLevelDebug("no synthetic ip found");
            return HandleResult.CONTINUE;
        }
        pkb.setMatchedIps(ips);
        return HandleResult.PASS;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        var packet = pkb.pkt.getPacket();
        if (packet instanceof ArpPacket) {
            return _returnnext(pkb, arpInput);
        } else if (packet instanceof AbstractIpPacket) {
            return _returnnext(pkb, ipRoute);
        }
        assert Logger.lowLevelDebug("cannot process the packet");
        if (pkb.debugger.isDebugOn()) {
            pkb.debugger
                .append("packet is not arp nor ip")
                .newLine();
        }
        return _returndrop(pkb);
    }
}
