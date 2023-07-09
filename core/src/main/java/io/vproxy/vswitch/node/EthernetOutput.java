package io.vproxy.vswitch.node;

import io.vproxy.base.util.Logger;
import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.iface.Iface;

public class EthernetOutput extends Node {
    private final NodeEgress devOutput = new NodeEgress("dev-output");
    private final NodeEgress multicastOutput = new NodeEgress("multicast-output");

    public EthernetOutput() {
        super("ethernet-output");
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        builder.addEdge("ethernet-output", "dev-output", "dev-output", DEFAULT_EDGE_DISTANCE);
        builder.addEdge("ethernet-output", "flood-output", "error-drop", DEFAULT_EDGE_DISTANCE);
        builder.addEdge("ethernet-output", "broadcast-output", "multicast-output", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        fillEdges(devOutput);
        fillEdges(multicastOutput);
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return HandleResult.PASS;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        var packet = pkb.pkt;
        var dst = packet.getDst();
        if (dst.isUnicast()) {
            assert Logger.lowLevelDebug("packet is unicast");

            // for unicast, we first check whether we can forward this packet out
            Iface iface = pkb.network.macTable.lookup(dst);
            if (iface != null) {
                pkb.devout = iface;
                return _returnnext(pkb, devOutput);
            }

            assert Logger.lowLevelDebug("dst not recorded in mac table");
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.line(d -> d.append("unable to find output dev"));
            }

            return _returndrop(pkb);
        } else {
            assert Logger.lowLevelDebug("packet is broadcast/multicast");
            // forward the broadcast message
            // and send the packet to local virtual hosts except the one that generated this message

            return _returnnext(pkb, multicastOutput);
        }
    }
}
