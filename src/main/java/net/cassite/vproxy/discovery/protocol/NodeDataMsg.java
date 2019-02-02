package net.cassite.vproxy.discovery.protocol;

import net.cassite.vproxy.component.exception.XException;
import net.cassite.vproxy.discovery.Node;

import java.net.UnknownHostException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class NodeDataMsg {
    public final int version;
    public final String type;
    public final List<Node> nodes;

    public NodeDataMsg(int version, String type, List<Node> nodes) {
        this.version = version;
        this.type = type;
        this.nodes = Collections.unmodifiableList(nodes);
    }

    public static NodeDataMsg parse(Object o) throws XException {
        if (!(o instanceof List)) {
            throw new XException("invalid message, not list");
        }
        List l = (List) o;
        if (l.size() < 3) {
            throw new XException("invalid message, list too short");
        }
        if (!(l.get(0) instanceof Integer)
            || !(l.get(1) instanceof String)
            || !(l.get(2) instanceof List)) {
            throw new XException("invalid message, list data type wrong");
        }

        int version = (int) l.get(0);
        String type = (String) l.get(1);
        List nodesL = (List) l.get(2);
        List<Node> nodes = new LinkedList<>();
        for (Object n : nodesL) {
            if (!(n instanceof List)) {
                throw new XException("invalid message, node element not list");
            }
            List nl = (List) n;
            if (nl.size() < 5) {
                throw new XException("invalid message, node list too short");
            }
            if (!(nl.get(0) instanceof String)
                || !(nl.get(1) instanceof String)
                || !(nl.get(2) instanceof Integer)
                || !(nl.get(3) instanceof Integer)
                || !(nl.get(4) instanceof Integer)) {
                throw new XException("invalid message, node list data type wrong");
            }

            String nodeName = (String) nl.get(0);
            String address = (String) nl.get(1);
            int udpPort = (int) nl.get(2);
            int tcpPort = (int) nl.get(3);
            boolean healthy = ((int) nl.get(4)) != 0;

            Node node;
            try {
                node = new Node(nodeName, address, udpPort, tcpPort);
            } catch (UnknownHostException e) {
                throw new XException("invalid message, address invalid");
            }
            node.healthy = healthy;

            nodes.add(node);
        }

        return new NodeDataMsg(version, type, nodes);
    }

    @Override
    public String toString() {
        return "NodeDataMsg{" +
            "version=" + version +
            ", type='" + type + '\'' +
            ", nodes=" + nodes +
            '}';
    }
}
