package vproxy.discovery.protocol;

import vjson.JSON;
import vproxy.component.exception.XException;
import vproxy.discovery.Node;

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

    public static NodeDataMsg parse(int version, String type, JSON.Array array) throws XException {
        List<Node> nodes = new LinkedList<>();
        for (int i = 0; i < array.length(); ++i) {
            JSON.Instance inst = array.get(i);
            if (!(inst instanceof JSON.Object)) {
                throw new XException("invalid message, element not json object");
            }
            JSON.Object nl = (JSON.Object) inst;
            if (!nl.containsKey("nodeName")
                || !nl.containsKey("address")
                || !nl.containsKey("udpPort")
                || !nl.containsKey("tcpPort")
                || !nl.containsKey("healthy")) {
                throw new XException("invalid message, missing keys");
            }
            if (!(nl.get("nodeName") instanceof JSON.String)
                || !(nl.get("address") instanceof JSON.String)
                || !(nl.get("udpPort") instanceof JSON.Integer)
                || !(nl.get("tcpPort") instanceof JSON.Integer)
                || !(nl.get("healthy") instanceof JSON.Bool)) {
                throw new XException("invalid message, value type wrong");
            }

            String nodeName = nl.getString("nodeName");
            String address = nl.getString("address");
            int udpPort = nl.getInt("udpPort");
            int tcpPort = nl.getInt("tcpPort");
            boolean healthy = nl.getBool("healthy");

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
