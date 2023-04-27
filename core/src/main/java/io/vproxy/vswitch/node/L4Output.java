package io.vproxy.vswitch.node;

import io.vproxy.base.util.Logger;
import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.SwitchDelegate;
import io.vproxy.vswitch.stack.conntrack.EnhancedTCPEntry;
import io.vproxy.vswitch.stack.conntrack.EnhancedUDPEntry;

public class L4Output extends Node {
    private final SwitchDelegate sw;
    private final NodeEgress ipOutput = new NodeEgress("ip-output");
    private final NodeEgress fastDevOutput = new NodeEgress("fast-dev-output");

    public L4Output(SwitchDelegate sw) {
        super("l4-output");
        this.sw = sw;
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        builder.addEdge("l4-output", "ip-output", "ip-output", DEFAULT_EDGE_DISTANCE);
        builder.addEdge("l4-output", "dev-output", "fast-dev-output", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        fillEdges(ipOutput);
        fillEdges(fastDevOutput);
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return HandleResult.PASS;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        if (pkb.udp != null) {
            assert Logger.lowLevelDebug("trying fastpath for udp");
            if (pkb.udp instanceof EnhancedUDPEntry) {
                var fastpath = ((EnhancedUDPEntry) pkb.udp).fastpath;
                if (fastpath != null && !fastpath.validateAndSetInto(sw, pkb)) {
                    fastpath = null;
                    ((EnhancedUDPEntry) pkb.udp).fastpath = null;
                }
                if (fastpath != null) {
                    assert Logger.lowLevelDebug("using fastpath for udp: " + fastpath);
                    pkb.devout = fastpath.output;
                    return _returnnext(pkb, fastDevOutput);
                } else {
                    assert Logger.lowLevelDebug("set recordFastPath for udp");
                    pkb.fastpath = true;
                }
            }
        } else if (pkb.tcp != null) {
            assert Logger.lowLevelDebug("trying fastpath for tcp");
            if (pkb.tcp instanceof EnhancedTCPEntry) {
                var fastpath = ((EnhancedTCPEntry) pkb.tcp).fastpath;
                if (fastpath != null && !fastpath.validateAndSetInto(sw, pkb)) {
                    fastpath = null;
                    ((EnhancedTCPEntry) pkb.tcp).fastpath = null;
                }
                if (fastpath != null) {
                    assert Logger.lowLevelDebug("using fastpath for tcp: " + fastpath);
                    pkb.devout = fastpath.output;
                    return _returnnext(pkb, fastDevOutput);
                } else {
                    assert Logger.lowLevelDebug("set recordFastPath for tcp");
                    pkb.fastpath = true;
                }
            }
        }

        assert Logger.lowLevelDebug("no fastpath, normal L3 output");
        return _returnnext(pkb, ipOutput);
    }

    public void output(PacketBuffer pkb) {
        pkb.next = null;
        var res = handle(pkb, null);
        if (res != HandleResult.DROP) {
            sw.scheduler.schedule(pkb);
        } else {
            assert Logger.lowLevelDebug("l4-output returns DROP");
        }
    }
}
