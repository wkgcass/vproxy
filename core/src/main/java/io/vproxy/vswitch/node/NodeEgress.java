package io.vproxy.vswitch.node;

import io.vproxy.commons.graph.GraphEdge;

import java.util.List;

public class NodeEgress {
    public final String name;
    public List<GraphEdge<Node>> edges;

    public NodeEgress(String name) {
        this.name = name;
    }
}
