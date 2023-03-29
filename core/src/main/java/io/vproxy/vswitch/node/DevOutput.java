package io.vproxy.vswitch.node;

import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.SwitchDelegate;

public class DevOutput extends Node {
    private final SwitchDelegate sw;

    public DevOutput(SwitchDelegate sw) {
        super("dev-output");
        this.sw = sw;
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
    }

    @Override
    protected void initNode() {
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return pkb.devout == null ? HandleResult.CONTINUE : HandleResult.PASS;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        if (pkb.devout == null) {
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger
                    .append("devout not found")
                    .newLine();
            }
            return HandleResult.DROP;
        }
        sw.sendPacket(pkb, pkb.devout);
        return _return(HandleResult.STOLEN, pkb);
    }
}
