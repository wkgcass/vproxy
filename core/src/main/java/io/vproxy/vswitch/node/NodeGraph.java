package io.vproxy.vswitch.node;

import io.vproxy.commons.graph.GraphBuilder;

public class NodeGraph extends GraphBuilder<Node> {
    public NodeGraph() {
    }

    public Node getNode(String name) {
        return nodes.get(name);
    }

    public void initGraph() {
        for (var n : nodes.values()) {
            n.initGraph(this);
        }
    }

    public void initNode() {
        for (var n : nodes.values()) {
            n.initNode();
            n.fillEdges(n.errorDrop);
        }
    }
}
