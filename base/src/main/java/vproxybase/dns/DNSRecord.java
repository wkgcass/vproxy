package vproxybase.dns;

import vfd.IP;
import vfd.IPPort;

public class DNSRecord {
    public final IP target;
    public final int port;
    public final int weight;
    public final String name;

    public DNSRecord(IP target) {
        this(target, 0, 0);
    }

    public DNSRecord(IPPort target) {
        this(target.getAddress(), target.getPort());
    }

    public DNSRecord(IP target, int port) {
        this(target, port, 0);
    }

    public DNSRecord(IP target, int port, String name) {
        this(target, port, 0, name);
    }

    public DNSRecord(IPPort target, int weight) {
        this(target.getAddress(), target.getPort(), weight);
    }

    public DNSRecord(IP target, int port, int weight) {
        this(target, port, weight, null);
    }

    public DNSRecord(IP target, int port, int weight, String name) {
        this.target = target;
        this.port = port;
        this.weight = weight;
        this.name = name;
    }
}
