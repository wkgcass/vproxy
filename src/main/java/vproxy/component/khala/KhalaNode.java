package vproxy.component.khala;

import java.util.Objects;

public class KhalaNode implements Comparable<KhalaNode> {
    public final String service;
    public final String zone;
    public final String address;
    public final int port;

    public KhalaNode(String service, String zone, String address, int port) {
        this.service = service;
        this.zone = zone;
        this.address = address;
        this.port = port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KhalaNode khalaNode = (KhalaNode) o;
        return port == khalaNode.port &&
            Objects.equals(service, khalaNode.service) &&
            Objects.equals(zone, khalaNode.zone) &&
            Objects.equals(address, khalaNode.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(service, zone, address, port);
    }

    @Override
    public String toString() {
        return "KhalaNode{" +
            "service='" + service + '\'' +
            ", zone='" + zone + '\'' +
            ", address='" + address + '\'' +
            ", port=" + port +
            '}';
    }

    @Override
    public int compareTo(KhalaNode that) {
        if (!this.service.equals(that.service)) return this.service.compareTo(that.service);
        if (!this.zone.equals(that.zone)) return this.zone.compareTo(that.zone);
        if (!this.address.equals(that.address)) return this.address.compareTo(that.address);
        if (this.port != that.port) return this.port - that.port;
        return 0;
    }
}
