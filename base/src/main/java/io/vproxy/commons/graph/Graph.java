package io.vproxy.commons.graph;

import java.util.*;

public class Graph<N extends GraphNode<N>> {
    protected final Set<N> nodes;

    public Graph() {
        this(Collections.emptySet());
    }

    public Graph(Collection<N> initialNodes) {
        this.nodes = new HashSet<>(initialNodes);
    }

    public Map<N, GraphPath<N>> shortestPaths(N from) {
        return shortestPaths(from, Collections.emptySet());
    }

    public Map<N, GraphPath<N>> shortestPaths(N from, Set<N> skipNodes) {
        if (!nodes.contains(from))
            throw new IllegalArgumentException("`from`=" + from + " is not contained in `nodes`");
        return Dijkstra.dijkstra(from, skipNodes);
    }

    public boolean containsNode(N node) {
        return nodes.contains(node);
    }

    public Set<N> gc(N gcroot) {
        return gc(Collections.singletonList(gcroot));
    }

    public Set<N> gc(List<N> gcroot) {
        for (var node : gcroot) {
            dfsGC(node);
        }
        var removed = new HashSet<N>();
        for (var iterator = nodes.iterator(); iterator.hasNext(); ) {
            var node = iterator.next();
            if (node.gc) {
                continue;
            }
            iterator.remove();
            removed.add(node);
        }
        // reset gc fields
        for (var node : nodes) {
            node.gc = false;
        }
        // remove edges
        var edgesToRemove = new ArrayList<GraphEdge<N>>(); // reuse the memory
        for (var node : nodes) {
            for (var e : node.allEdges()) {
                if (removed.contains(e.to)) {
                    edgesToRemove.add(e);
                }
            }
            for (var e : edgesToRemove) {
                node.deregister(e);
            }
            edgesToRemove.clear();
        }
        return removed;
    }

    private void dfsGC(N node) {
        if (node.gc) { // already reached
            return;
        }
        node.gc = true;
        for (var edge : node.allEdges()) {
            dfsGC(edge.to);
        }
    }

    public String toMermaidString() {
        var sb = new StringBuilder();
        sb.append("graph LR\n");
        var nodes = new HashMap<N, String>();
        var id = 0;
        for (var n : this.nodes) {
            var idStr = "N" + (++id);
            nodes.put(n, idStr);
            sb.append(idStr).append("[").append(n.name).append("]\n");
        }
        for (var n : this.nodes) {
            var nId = nodes.get(n);
            for (var e : n.allEdges()) {
                var toId = nodes.get(e.to);
                sb.append(nId).append(" -->|").append(e.getDistance()).append("| ").append(toId).append("\n");
            }
        }
        return sb.toString();
    }
}
