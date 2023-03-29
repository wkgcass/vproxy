package io.vproxy.commons.graph;

import java.util.*;

public class GraphNode<N extends GraphNode<N>> {
    public final String name;
    private final Map<GraphNode<N>, Set<GraphEdge<N>>> edges = new HashMap<>();
    private final Map<String, List<GraphEdge<N>>> nameMap = new HashMap<>();
    private final Set<GraphEdge<N>> allEdges = new HashSet<>();

    public GraphNode(String name) {
        this.name = name;
    }

    public boolean isLinkedToNextNode(GraphNode<N> node) {
        return edges.containsKey(node);
    }

    public boolean hasNamedEdge(String name) {
        return nameMap.containsKey(name);
    }

    public void register(GraphEdge<N> edge) {
        var set = edges.get(edge.to);
        //noinspection Java8MapApi
        if (set == null) {
            set = new HashSet<>();
            edges.put(edge.to, set);
        }
        set.add(edge);
        allEdges.add(edge);

        if (edge.name != null) {
            var ls = nameMap.get(edge.name);
            //noinspection Java8MapApi
            if (ls == null) {
                ls = new ArrayList<>();
                nameMap.put(edge.name, ls);
            }
            ls.add(edge);
            ls.sort((a, b) -> (int) (a.distance - b.distance));
        }
    }

    public Collection<GraphEdge<N>> allEdges() {
        return allEdges;
    }

    public List<GraphEdge<N>> getEdges(String edgeName) {
        return nameMap.get(edgeName);
    }

    @Override
    public String toString() {
        return "{" + name + "}";
    }
}
