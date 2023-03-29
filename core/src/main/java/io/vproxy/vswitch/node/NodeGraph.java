package io.vproxy.vswitch.node;

import io.vproxy.commons.graph.GraphBuilder;

public class NodeGraph extends GraphBuilder<Node> {
    public NodeGraph() {
    }

    public Node getNode(String name) {
        return nodes.get(name);
    }
}
