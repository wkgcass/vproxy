package vproxy.dns;

import vfd.IP;
import vfd.IPPort;

public class Record {
    public final IP target;
    public final int port;
    public final int weight;
    public final String name;

    public Record(IP target) {
        this(target, 0, 0);
    }

    public Record(IPPort target) {
        this(target.getAddress(), target.getPort());
    }

    public Record(IP target, int port) {
        this(target, port, 0);
    }

    public Record(IP target, int port, String name) {
        this(target, port, 0, name);
    }

    public Record(IPPort target, int weight) {
        this(target.getAddress(), target.getPort(), weight);
    }

    public Record(IP target, int port, int weight) {
        this(target, port, weight, null);
    }

    public Record(IP target, int port, int weight, String name) {
        this.target = target;
        this.port = port;
        this.weight = weight;
        this.name = name;
    }
}
