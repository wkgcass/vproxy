package io.vproxy.vproxyx.nexus;

import io.vproxy.commons.graph.GraphNode;

public class NexusNode extends GraphNode<NexusNode> {
    public final NexusPeer peer;

    public NexusNode(String name, NexusPeer peer) {
        super(name);
        this.peer = peer;
    }
}
