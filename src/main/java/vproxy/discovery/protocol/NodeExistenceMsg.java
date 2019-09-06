package vproxy.discovery.protocol;

import vjson.JSON;
import vproxy.component.exception.XException;

public class NodeExistenceMsg {
    public final int version;
    public final String type;
    public final String nodeName;
    public final int udpPort;
    public final int tcpPort;
    public final String hash;

    public NodeExistenceMsg(int version, String type, String nodeName, int udpPort, int tcpPort, String hash) {
        this.version = version;
        this.type = type;
        this.nodeName = nodeName;
        this.udpPort = udpPort;
        this.tcpPort = tcpPort;
        this.hash = hash;
    }

    public static NodeExistenceMsg parse(JSON.Object o) throws XException {
        if (!o.containsKey("version")
            || !o.containsKey("type")
            || !o.containsKey("nodeName")
            || !o.containsKey("udpPort")
            || !o.containsKey("tcpPort")
            || !o.containsKey("hash")) {
            throw new XException("invalid message, missing some keys: " + o);
        }
        if (!(o.get("version") instanceof JSON.Integer)
            || !(o.get("type") instanceof JSON.String)
            || !(o.get("nodeName") instanceof JSON.String)
            || !(o.get("udpPort") instanceof JSON.Integer)
            || !(o.get("tcpPort") instanceof JSON.Integer)
            || !(o.get("hash") instanceof JSON.String)) {
            throw new XException("invalid message, value type wrong: " + o);
        }

        int version = o.getInt("version");
        String type = o.getString("type");
        String nodeName = o.getString("nodeName");
        int udpPort = o.getInt("udpPort");
        int tcpPort = o.getInt("tcpPort");
        String hash = o.getString("hash");

        return new NodeExistenceMsg(version, type, nodeName, udpPort, tcpPort, hash);
    }

    @Override
    public String toString() {
        return "NodeExistenceMsg{" +
            "version=" + version +
            ", type='" + type + '\'' +
            ", nodeName='" + nodeName + '\'' +
            ", udpPort=" + udpPort +
            ", tcpPort=" + tcpPort +
            ", hash='" + hash + '\'' +
            '}';
    }
}
