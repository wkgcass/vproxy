package io.vproxy.vswitch.node;

import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.SwitchDelegate;
import io.vproxy.vswitch.iface.Iface;

import java.util.HashSet;
import java.util.Set;

public class BroadcastOutput extends Node {
    private final SwitchDelegate sw;
    private final NodeEgress devOutput = new NodeEgress("dev-output");

    public BroadcastOutput(SwitchDelegate sw) {
        super("broadcast-output");
        this.sw = sw;
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        builder.addEdge("broadcast-output", "dev-output", "dev-output", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        fillEdges(devOutput);
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return HandleResult.PASS;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        Set<Iface> sent = new HashSet<>();
        if (pkb.devin != null) {
            sent.add(pkb.devin);
        }
        var isFirst = true;
        HandleResult res = HandleResult.DROP;
        for (Iface f : sw.getIfaces()) {
            // send if vrf matches
            if (f.getLocalSideVrf(pkb.vrf) != pkb.vrf) {
                continue;
            }
            // no duplicated sending
            if (!sent.add(f)) {
                continue;
            }
            if (pkb.ensurePartialPacketParsed()) {
                if (pkb.debugger.isDebugOn()) {
                    pkb.debugger.append("invalid packet");
                }
                return _returndropSkipErrorDrop();
            }
            if (isFirst) {
                isFirst = false;
                pkb.devout = f;
                res = _next(pkb, devOutput);
            } else {
                var copied = pkb.copy();
                copied.devout = f;
                _schedule(scheduler, copied, devOutput);
            }
        }
        return _return(res, pkb, scheduler);
    }
}
