package vproxy.dns;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class Record {
    public final InetAddress target;
    public final int port;
    public final int weight;
    public final String name;

    public Record(InetAddress target) {
        this(target, 0, 0);
    }

    public Record(InetSocketAddress target) {
        this(target.getAddress(), target.getPort());
    }

    public Record(InetAddress target, int port) {
        this(target, port, 0);
    }

    public Record(InetSocketAddress target, int weight) {
        this(target.getAddress(), target.getPort(), weight);
    }

    public Record(InetAddress target, int port, int weight) {
        this(target, port, weight, null);
    }

    public Record(InetAddress target, int port, int weight, String name) {
        this.target = target;
        this.port = port;
        this.weight = weight;
        this.name = name;
    }
}
