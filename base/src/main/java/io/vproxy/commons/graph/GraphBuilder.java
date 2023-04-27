package io.vproxy.commons.graph;

import java.util.HashMap;
import java.util.Map;

public class GraphBuilder<N extends GraphNode<N>> {

    protected final Map<String, N> nodes = new HashMap<>();

    public GraphBuilder<N> addNode(N n) {
        if (nodes.containsKey(n.name))
            throw new IllegalArgumentException("`node`=" + n + " is already registered");
        if (nodes.containsValue(n))
            throw new IllegalArgumentException("`node`=" + n + " is already registered");
        nodes.put(n.name, n);
        return this;
    }

    public GraphBuilder<N> addTwoWayEdges(N a, N b, long distance) {
        addEdge(a, b, distance);
        addEdge(b, a, distance);
        return this;
    }

    public GraphBuilder<N> addEdge(String from, String to, long distance) {
        return addEdge(from, to, null, distance);
    }

    public GraphBuilder<N> addEdge(String from, String to, String edgeName, long distance) {
        var fromN = nodes.get(from);
        var toN = nodes.get(to);
        if (fromN == null)
            throw new IllegalArgumentException("node `from`=" + from + " does not exist");
        if (toN == null)
            throw new IllegalArgumentException("node `to`=" + to + " does not exist");
        return addEdge(fromN, toN, edgeName, distance);
    }

    public GraphBuilder<N> addEdge(N from, N to, long distance) {
        return addEdge(from, to, null, distance);
    }

    public GraphBuilder<N> addEdge(N from, N to, String edgeName, long distance) {
        return addEdge(new GraphEdge<>(edgeName, from, to, distance));
    }

    public GraphBuilder<N> addEdge(GraphEdge<N> edge) {
        if (edge.distance < 0) {
            throw new IllegalArgumentException("`distance`=" + edge.distance + " < 0");
        }
        if (!nodes.containsValue(edge.from)) {
            throw new IllegalArgumentException("`from`=" + edge.from + " is not a registered node");
        }
        if (!nodes.containsValue(edge.to)) {
            throw new IllegalArgumentException("`to`=" + edge.to + " is not a registered node");
        }
        edge.from.register(edge);
        return this;
    }

    public Graph<N> build() {
        return new Graph<>(nodes.values());
    }
}
