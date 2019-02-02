package net.cassite.vproxy.discovery.protocol;

import net.cassite.vproxy.component.exception.XException;

import java.util.List;

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

    public static NodeExistenceMsg parse(Object o) throws XException {
        if (!(o instanceof List)) {
            throw new XException("invalid message, not list");
        }
        List l = (List) o;
        if (l.size() < 6) {
            throw new XException("invalid message, list too short");
        }
        if (!(l.get(0) instanceof Integer)
            || !(l.get(1) instanceof String)
            || !(l.get(2) instanceof String)
            || !(l.get(3) instanceof Integer)
            || !(l.get(4) instanceof Integer)
            || !(l.get(5) instanceof String)) {
            throw new XException("invalid message, list data type wrong");
        }

        int version = (int) l.get(0);
        String type = (String) l.get(1);
        String nodeName = (String) l.get(2);
        int udpPort = (int) l.get(3);
        int tcpPort = (int) l.get(4);
        String hash = (String) l.get(5);

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
