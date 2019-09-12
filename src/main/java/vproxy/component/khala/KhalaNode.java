package vproxy.component.khala;

import vjson.JSON;

import java.util.Objects;

public class KhalaNode implements Comparable<KhalaNode> {
    public final String service;
    public final String zone;
    public final String address;
    public final int port;

    // this field will NOT be checked when doing equals or hashCode
    public final JSON.Object meta;

    public KhalaNode(String service, String zone, String address, int port, JSON.Object meta) {
        this.service = service;
        this.zone = zone;
        this.address = address;
        this.port = port;
        this.meta = meta;
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
        // should NOT check `meta` field
    }

    @Override
    public int hashCode() {
        return Objects.hash(service, zone, address, port);
        // should NOT check `meta` field
    }

    @Override
    public String toString() {
        return "KhalaNode{" +
            "service='" + service + '\'' +
            ", zone='" + zone + '\'' +
            ", address='" + address + '\'' +
            ", port=" + port +
            ", meta=" + meta.stringify() +
            '}';
    }

    @Override
    public int compareTo(KhalaNode that) {
        if (!this.service.equals(that.service)) return this.service.compareTo(that.service);
        if (!this.zone.equals(that.zone)) return this.zone.compareTo(that.zone);
        if (!this.address.equals(that.address)) return this.address.compareTo(that.address);
        if (this.port != that.port) return this.port - that.port;
        return 0;
        // should NOT check `meta` field
    }
}
