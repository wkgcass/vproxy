package vproxy.component.khala.protocol;

import vjson.JSON;
import vproxy.component.exception.XException;
import vproxy.component.khala.KhalaNode;
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

    private static KhalaNode getKhalaNode(JSON.Object o) throws XException {
        if (!o.containsKey("service")
            || !o.containsKey("zone")
            || !o.containsKey("address")
            || !o.containsKey("port")
            || !o.containsKey("meta")) {
            throw new XException("invalid message, missing khalaNode keys");
        }
        if (!(o.get("service") instanceof JSON.String)
            || !(o.get("zone") instanceof JSON.String)
            || !(o.get("address") instanceof JSON.String)
            || !(o.get("port") instanceof JSON.Integer)
            || !(o.get("meta") instanceof JSON.Object)) {
            throw new XException("invalid message, wrong khalaNode value type");
        }
        return new KhalaNode(o.getString("service"), o.getString("zone"), o.getString("address"), o.getInt("port"), o.getObject("meta"));
    }

    private static Node getNode(JSON.Object o) throws XException {
        if (!o.containsKey("nodeName")
            || !o.containsKey("address")
            || !o.containsKey("udpPort")
            || !o.containsKey("tcpPort")) {
            throw new XException("invalid message, missing node keys");
        }
        if (!(o.get("nodeName") instanceof JSON.String)
            || !(o.get("address") instanceof JSON.String)
            || !(o.get("udpPort") instanceof JSON.Integer)
            || !(o.get("tcpPort") instanceof JSON.Integer)) {
            throw new XException("invalid message, wrong node value type");
        }
        try {
            return new Node(o.getString("nodeName"), o.getString("address"), o.getInt("udpPort"), o.getInt("tcpPort"));
        } catch (UnknownHostException e) {
            throw new XException(o.getString("address") + " is not a valid address");
        }
    }

    private static void validateKhalaLocal(JSON.Instance data) throws XException {
        if (!(data instanceof JSON.Object)) {
            throw new XException("invalid body type");
        }
        JSON.Object body = (JSON.Object) data;

        if (!body.containsKey("node") || !body.containsKey("khalaNodes")) {
            throw new XException("missing node or khalaNodes in request body");
        }
        if (!(body.get("node") instanceof JSON.Object)
            || !(body.get("khalaNodes") instanceof JSON.Array)) {
            throw new XException("wrong value type of node or khalaNodes");
        }
        var arr = body.getArray("khalaNodes");
        for (int i = 0; i < arr.length(); ++i) {
            JSON.Instance inst = arr.get(i);
            if (!(inst instanceof JSON.Object)) {
                throw new XException("wrong value type of khalaNodes[" + i + "]");
            }
        }
    }

    public static KhalaMsg parse(int version, String type, JSON.Instance data) throws XException {
        switch (type) {
            case "khala.add":
            case "khala.remove": {
                if (!(data instanceof JSON.Object)) {
                    throw new XException("invalid body type");
                }
                JSON.Object body = (JSON.Object) data;

                if (!body.containsKey("node") || !body.containsKey("khalaNode")) {
                    throw new XException("missing node or khalaNode in request body");
                }
                if (!(body.get("node") instanceof JSON.Object)
                    || !(body.get("khalaNode") instanceof JSON.Object)) {
                    throw new XException("wrong value type of node or khalaNode");
                }
                Node n = getNode(body.getObject("node"));
                KhalaNode kn = getKhalaNode(body.getObject("khalaNode"));
                return new KhalaMsg(version, type, new HashMap<>() {{
                    put(n, Collections.singletonList(kn));
                }});
            }
            case "khala.local": {
                validateKhalaLocal(data);
                JSON.Object body = (JSON.Object) data;
                Node n = getNode(body.getObject("node"));
                List<KhalaNode> kns = new LinkedList<>();
                var arr = body.getArray("khalaNodes");
                for (int i = 0; i < arr.length(); ++i) {
                    kns.add(getKhalaNode(arr.getObject(i)));
                }
                return new KhalaMsg(version, type, new HashMap<>() {{
                    put(n, kns);
                }});
            }
            case "khala.sync":
                if (!(data instanceof JSON.Array)) {
                    throw new XException("invalid body type");
                }
                JSON.Array array = (JSON.Array) data;

                var map = new HashMap<Node, List<KhalaNode>>();
                for (int i = 0; i < array.length(); ++i) {
                    JSON.Instance inst = array.get(i);
                    validateKhalaLocal(inst);
                    JSON.Object o = (JSON.Object) inst;
                    Node n = getNode(o.getObject("node"));
                    List<KhalaNode> kns = new LinkedList<>();
                    var arr = o.getArray("khalaNodes");
                    for (int j = 0; j < arr.length(); ++j) {
                        kns.add(getKhalaNode(arr.getObject(j)));
                    }
                    map.put(n, kns);
                }
                return new KhalaMsg(version, type, map);
            default:
                throw new XException("unknown type " + type);
        }
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
