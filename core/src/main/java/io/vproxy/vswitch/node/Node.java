package io.vproxy.vswitch.node;

import io.vproxy.base.util.Logger;
import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.commons.graph.GraphNode;
import io.vproxy.vswitch.PacketBuffer;

public abstract class Node extends GraphNode<Node> {
    protected static final int DEFAULT_EDGE_DISTANCE = 10_000;
    protected static final int BUILT_IN_DISTANCE_DELTA = 100;

    protected final NodeEgress errorDrop = new NodeEgress("error-drop");

    public Node(String name) {
        super(name);
    }

    // called after all nodes are added into the graph
    protected abstract void initGraph(GraphBuilder<Node> builder);

    // called after the node graph is ready
    protected abstract void initNode();

    // the code in preHandle() is executed in the caller node
    protected abstract HandleResult preHandle(PacketBuffer pkb);

    // the code in handle() is executed in this node
    protected abstract HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler);

    protected void fillEdges(NodeEgress egress) {
        egress.edges = getEdges(egress.name);
    }

    protected HandleResult _return(HandleResult res, PacketBuffer pkb) {
        if (res == HandleResult.DROP) {
            return _next(pkb, errorDrop);
        }
        return res;
    }

    protected HandleResult _return(HandleResult res, PacketBuffer pkb, NodeGraphScheduler scheduler) {
        if (res == HandleResult.DROP && scheduler.isGenerated()) {
            return _returndropSkipErrorDrop();
        }
        return _return(res, pkb);
    }

    protected HandleResult _returndrop(PacketBuffer pkb) {
        return _next(pkb, errorDrop);
    }

    protected HandleResult _returndropSkipErrorDrop() {
        return HandleResult.DROP;
    }

    protected HandleResult _returnnext(PacketBuffer pkb, NodeEgress egress) {
        var res = _next(pkb, egress);
        return _return(res, pkb);
    }

    // will always return DROP or PICK
    protected HandleResult _next(PacketBuffer pkb, NodeEgress egress) {
        assert Logger.lowLevelDebug("try _next: " + egress.name);
        if (pkb.debugger.isDebugOn()) {
            pkb.debugger.append("select node on edge ").append(name).append("/").append(egress.name).append(": ");
        }
        if (egress.edges == null) {
            if (pkb.debugger.isDebugOn()) {
                pkb.debugger.append("no node exists").newLine();
            }
            return HandleResult.DROP;
        }
        for (var ite = egress.edges.iterator(); ite.hasNext(); ) {
            var e = ite.next();
            var res = e.to.preHandle(pkb);
            switch (res) {
                case PASS:
                    if (!ite.hasNext()) {
                        pkb.next = e.to;
                        if (pkb.debugger.isDebugOn()) {
                            pkb.debugger
                                .append("last node returns PASS: ").append(e.to.name)
                                .newLine();
                        }
                        return HandleResult.PICK;
                    }
                case CONTINUE:
                    continue;
                case PICK:
                case STOLEN:
                    pkb.next = e.to;
                    if (pkb.debugger.isDebugOn()) {
                        pkb.debugger
                            .append("node returns ").append(res.name())
                            .append(": ").append(e.to.name)
                            .newLine();
                    }
                    return HandleResult.PICK;
                default:
                    if (pkb.debugger.isDebugOn()) {
                        pkb.debugger
                            .append("node returns ").append(res.name())
                            .append(": ").append(e.to.name)
                            .newLine();
                    }
                    return HandleResult.DROP;
            }
        }
        if (pkb.debugger.isDebugOn()) {
            pkb.debugger
                .append("should not reach here, and nothing matches")
                .newLine();
        }
        return HandleResult.DROP;
    }

    protected void _schedule(NodeGraphScheduler scheduler, PacketBuffer pkb, NodeEgress egress) {
        if (_next(pkb, egress) != HandleResult.DROP) {
            scheduler.schedule(pkb);
        }
    }
}
