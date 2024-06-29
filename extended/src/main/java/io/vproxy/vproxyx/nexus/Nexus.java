package io.vproxy.vproxyx.nexus;

import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.commons.graph.Graph;
import io.vproxy.commons.graph.GraphEdge;
import io.vproxy.vproxyx.nexus.entity.LinkPeer;
import io.vproxy.vproxyx.nexus.entity.LinkReq;

import java.util.*;

public class Nexus extends Graph<NexusNode> {
    // name -> Node
    private final Map<String, NexusNode> nodes = new HashMap<>();
    private NexusNode selfNode;

    public void setSelfNode(NexusNode node) {
        if (selfNode != null) {
            throw new IllegalStateException("self node has already been set");
        }
        selfNode = node;
        nodes.put(selfNode.name, selfNode);
        super.nodes.add(selfNode);
    }

    public NexusNode getSelfNode() {
        return selfNode;
    }

    public void addNode(NexusNode from, NexusNode toNewNode, long distance) {
        Logger.trace(LogType.ALERT, "new node connected: " + toNewNode.name);

        var edge1 = new GraphEdge<>(from, toNewNode, distance);
        var edge2 = new GraphEdge<>(toNewNode, from, distance);
        from.register(edge1);
        toNewNode.register(edge2);

        nodes.put(toNewNode.name, toNewNode);
        super.nodes.add(toNewNode);
    }

    public void remove(NexusNode node) {
        Logger.trace(LogType.ALERT, "node removed: " + node.name);

        for (var e : node.allEdges()) {
            e.to.deregisterEdgesTo(node);
        }
        gc();
    }

    private void gc() {
        var removed = super.gc(selfNode);
        for (var n : removed) {
            Logger.trace(LogType.ALERT, "node " + n.name + " is unreachable");
            nodes.remove(n.name);
        }
    }

    public NexusNode getNode(String name) {
        return nodes.get(name);
    }

    // return [nextNode, ...nodePath]
    // may return [selfNode] if selfNode == dstNode
    // the returned list is mutable, so you can call removeFirst() to retrieve nextNode and nodePath
    public List<NexusNode> shortestPathTo(NexusNode dstNode) {
        assert selfNode != null;
        if (selfNode == dstNode) {
            var result = new ArrayList<NexusNode>();
            result.add(selfNode);
            return result;
        }

        var path = shortestPaths(selfNode).get(dstNode);
        if (path == null) {
            return null;
        }
        var result = new LinkedList<NexusNode>();
        for (var n : path.path) {
            result.add(n.to);
        }
        return result;
    }

    public void update(LinkReq req) {
        var node = getNode(req.node);
        if (node == selfNode)
            return;

        var peers = new HashMap<String, LinkPeer>();
        for (var n : req.peers) {
            peers.put(n.node, n);
        }

        if (node == null) {
            Logger.trace(LogType.ALERT, "new node synced: " + req.node);
            node = new NexusNode(req.node, null);
            nodes.put(node.name, node);
            super.nodes.add(node);
        }

        var edges = node.allEdges();
        var edgeMap = new HashMap<String, GraphEdge<NexusNode>>();
        for (var e : edges) {
            edgeMap.put(e.to.name, e);
        }

        var toAdd = new ArrayList<LinkPeer>();
        var toRemove = new ArrayList<GraphEdge<NexusNode>>();
        for (var e : edges) {
            if (peers.containsKey(e.to.name)) {
                var link = peers.get(e.to.name);
                e.setDistance(link.cost);
            } else {
                toRemove.add(e);
            }
        }
        for (var n : req.peers) {
            if (!edgeMap.containsKey(n.node)) {
                toAdd.add(n);
            }
        }

        for (var e : toRemove) {
            Logger.trace(LogType.ALERT, "edges removed between " + node.name + " and " + e.to.name);
            node.deregisterEdgesTo(e.to);
            e.to.deregisterEdgesTo(node);
        }
        for (var n : toAdd) {
            var to = getNode(n.node);
            if (to == null)
                continue;
            Logger.trace(LogType.ALERT, "edges added between " + node.name + " and " + to.name);

            var edge1 = new GraphEdge<>(node, to, n.cost);
            node.register(edge1);

            final var fnode = node;
            if (to.allEdges().stream().noneMatch(e -> e.to == fnode)) {
                var edge2 = new GraphEdge<>(to, node, Integer.MAX_VALUE / 2);
                to.register(edge2);
            }
        }
        gc();
    }
}
