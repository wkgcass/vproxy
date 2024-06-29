package io.vproxy.commons.graph;

import java.util.Objects;

public class GraphEdge<N extends GraphNode<N>> {
    public final String name;
    public final N from;
    public final N to;
    private long distance;

    public GraphEdge(N from, N to, long distance) {
        this(null, from, to, distance);
    }

    public GraphEdge(String name, N from, N to, long distance) {
        this.name = name;
        this.from = from;
        this.to = to;
        setDistance(distance);
    }

    public long getDistance() {
        return distance;
    }

    public void setDistance(long distance) {
        if (distance < 0) {
            distance = 0;
        }
        this.distance = distance;
    }

    @Override
    public String toString() {
        if (name != null)
            return "{" + name + ":" + from + "---" + distance + "-->" + to + "}";
        return "{" + from + "---" + distance + "-->" + to + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GraphEdge<?> graphEdge = (GraphEdge<?>) o;

        if (!Objects.equals(from, graphEdge.from)) return false;
        return Objects.equals(to, graphEdge.to);
    }

    @Override
    public int hashCode() {
        int result = from != null ? from.hashCode() : 0;
        result = 31 * result + (to != null ? to.hashCode() : 0);
        return result;
    }
}
