package net.cassite.vproxy.component.khala;

import java.util.Objects;

public class KhalaNode {
    public final KhalaNodeType type;
    public final String service;
    public final String zone;
    public final int port;

    public KhalaNode(KhalaNodeType type, String service, String zone, int port) {
        this.type = type;
        this.service = service;
        this.zone = zone;
        this.port = port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KhalaNode khalaNode = (KhalaNode) o;
        return port == khalaNode.port &&
            type == khalaNode.type &&
            Objects.equals(service, khalaNode.service) &&
            Objects.equals(zone, khalaNode.zone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, service, zone, port);
    }

    @Override
    public String toString() {
        return "KhalaNode{" +
            "type=" + type +
            ", service='" + service + '\'' +
            ", zone='" + zone + '\'' +
            ", port=" + port +
            '}';
    }
}
