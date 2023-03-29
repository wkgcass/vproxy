package io.vproxy.vswitch.node;

import io.vproxy.base.util.Logger;
import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.iface.Iface;

public class UnicastInput extends Node {
    private final NodeEgress localUnicastInput = new NodeEgress("local-unicast-input");
    private final NodeEgress devOutput = new NodeEgress("dev-output");

    public UnicastInput() {
        super("unicast-input");
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        builder.addEdge("unicast-input", "local-unicast-input", "local-unicast-input", DEFAULT_EDGE_DISTANCE);
        builder.addEdge("unicast-input", "dev-output", "dev-output", DEFAULT_EDGE_DISTANCE);
        builder.addEdge("unicast-input", "flood-output", "error-drop", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        fillEdges(localUnicastInput);
        fillEdges(devOutput);
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return HandleResult.PASS;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        // for unicast, we first check whether we can forward this packet out

        MacAddress dst = pkb.pkt.getDst();
        Iface output = pkb.network.macTable.lookup(dst);
        if (pkb.debugger.isDebugOn()) {
            pkb.debugger.line(d -> d.append("mac lookup output dev is ")
                .append(output == null ? "null" : output.name()));
        }
        if (output != null) {
            if (pkb.devin == null || pkb.devin != output) {
                pkb.devout = output;
                return _returnnext(pkb, devOutput);
            } else {
                assert Logger.lowLevelDebug("drop the packet which would be forwarded out to the same interface as the input interface: " + pkb);
                if (pkb.debugger.isDebugOn()) {
                    pkb.debugger.line(d -> d.append("the output dev is the same as the input"));
                }
                return _returndropSkipErrorDrop();
            }
        }

        assert Logger.lowLevelDebug("dst not recorded in mac table");

        // check and pass to local
        return _returnnext(pkb, localUnicastInput);
    }
}
