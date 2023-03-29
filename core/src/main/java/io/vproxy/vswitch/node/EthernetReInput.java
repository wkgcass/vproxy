package io.vproxy.vswitch.node;

import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vswitch.PacketBuffer;

public class EthernetReInput extends Node {
    private final NodeEgress ethernetInput = new NodeEgress("ethernet-input");

    public EthernetReInput() {
        super("ethernet-reinput");
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        builder.addEdge("ethernet-reinput", "ethernet-input", "ethernet-input", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        fillEdges(ethernetInput);
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return HandleResult.PASS;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        pkb.devin = null; // clear devin before re-input
        pkb.clearHelperFields();
        return _returnnext(pkb, ethernetInput);
    }
}
