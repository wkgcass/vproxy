package vproxy.component.khala.protocol;

import vproxy.component.exception.XException;
import vproxy.component.khala.KhalaNode;
import vproxy.component.khala.KhalaNodeType;
import vproxy.discovery.Node;

import java.net.UnknownHostException;
import java.util.*;

public class KhalaMsg {
    public final int version;
    public final String type;
    public final Map<Node, List<KhalaNode>> nodes;

    public KhalaMsg(int version, String type, Map<Node, List<KhalaNode>> nodes) {
        this.version = version;
        this.type = type;
        this.nodes = Collections.unmodifiableMap(nodes);
    }

    public static KhalaMsg parse(List msg) throws XException {
        if (msg.size() < 3)
            throw new XException("invalid message, list too short");
        int version = (int) msg.get(0);
        String type = (String) msg.get(1);
        if (!(msg.get(2) instanceof List))
            throw new XException("invalid message, wrong format");
        List nodes = (List) msg.get(2);

        Map<Node, List<KhalaNode>> nodeMap = new HashMap<>();
        for (Object e : nodes) {
            if (!(e instanceof List))
                throw new XException("invalid message, element wrong format");
            List nNodes = (List) e;
            if (nNodes.size() < 5)
                throw new XException("invalid message, element list too short");
            if (!(nNodes.get(0) instanceof String) || !(nNodes.get(1) instanceof String)
                || !(nNodes.get(2) instanceof Integer) || !(nNodes.get(3) instanceof Integer)
                || !(nNodes.get(4) instanceof List))
                throw new XException("invalid message, element list wrong format");
            String nodeName = (String) nNodes.get(0);
            String address = (String) nNodes.get(1);
            int udpPort = (int) nNodes.get(2);
            int tcpPort = (int) nNodes.get(3);
            Node n;
            try {
                n = new Node(nodeName, address, udpPort, tcpPort);
            } catch (UnknownHostException e1) {
                throw new XException(address + " is not a valid address");
            }
            List<KhalaNode> list = new LinkedList<>();
            nodeMap.put(n, list);

            List kNodes = (List) nNodes.get(4);
            for (Object ee : kNodes) {
                if (!(ee instanceof List))
                    throw new XException("invalid message, khala-node is not a list");

                List kNode = (List) ee;
                if (kNode.size() < 5)
                    throw new XException("invalid message, khala-node list too short");
                if (!(kNode.get(0) instanceof String)
                    || !(kNode.get(1) instanceof String)
                    || !(kNode.get(2) instanceof String)
                    || !(kNode.get(3) instanceof String)
                    || !(kNode.get(4) instanceof Integer))
                    throw new XException("invalid message, khala-node wrong format");
                String kType = (String) kNode.get(0);
                if (!kType.equals("nexus") && !kType.equals("pylon"))
                    throw new XException("invalid message, khala-node type is wrong");
                String service = (String) kNode.get(1);
                String zone = (String) kNode.get(2);
                String kNAddress = (String) kNode.get(3);
                int port = (int) kNode.get(4);

                KhalaNode kn = new KhalaNode(KhalaNodeType.valueOf(kType), service, zone, kNAddress, port);
                list.add(kn);
            }
        }
        return new KhalaMsg(version, type, nodeMap);
    }

    @Override
    public String toString() {
        return "KhalaMsg{" +
            "version=" + version +
            ", type='" + type + '\'' +
            ", nodes=" + nodes +
            '}';
    }
}
