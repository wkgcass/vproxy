package io.vproxy.vswitch.node;

import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vswitch.PacketBuffer;

public class MulticastInput extends Node {
    private final NodeEgress localMulticastInput = new NodeEgress("local-multicast-input");
    private final NodeEgress multicastOutput = new NodeEgress("multicast-output");

    public MulticastInput() {
        super("multicast-input");
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        builder.addEdge("multicast-input", "local-broadcast-input", "local-multicast-input", DEFAULT_EDGE_DISTANCE);
        builder.addEdge("multicast-input", "broadcast-output", "multicast-output", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        fillEdges(localMulticastInput);
        fillEdges(multicastOutput);
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return HandleResult.PASS;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        // forward the broadcast message
        // and send the packet to local virtual hosts

        if (pkb.ensurePartialPacketParsed()) {
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.append("invalid packet");
            }
            return _returndropSkipErrorDrop();
        }
        var p = pkb.copy();
        _schedule(scheduler, p, multicastOutput);
        return _returnnext(p, localMulticastInput);
    }
}
