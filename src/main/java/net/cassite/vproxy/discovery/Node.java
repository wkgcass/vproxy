package net.cassite.vproxy.discovery;

import net.cassite.vproxy.dns.Resolver;
import net.cassite.vproxy.util.BlockCallback;
import net.cassite.vproxy.util.Utils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

public class Node implements Comparable<Node> {
    public final String nodeName;
    public final String address;
    public final int udpPort;
    public final int tcpPort;
    public boolean healthy = false; // default the node is unhealthy, will become healthy in hc callback

    public final InetAddress inetAddress;

    public Node(String nodeName, String address, int udpPort, int tcpPort) throws UnknownHostException {
        this.nodeName = nodeName;
        this.udpPort = udpPort;
        this.tcpPort = tcpPort;

        BlockCallback<InetAddress, UnknownHostException> cb = new BlockCallback<>();
        Resolver.getDefault().resolve(address, cb);
        this.inetAddress = cb.block();

        // use the same format for ip
        this.address = Utils.ipStr(this.inetAddress.getAddress());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return udpPort == node.udpPort &&
            tcpPort == node.tcpPort &&
            Objects.equals(nodeName, node.nodeName) &&
            Objects.equals(address, node.address) &&
            Objects.equals(inetAddress, node.inetAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeName, address, inetAddress, udpPort, tcpPort);
    }

    @Override
    public int compareTo(Node o) {
        if (!address.equals(o.address)) return address.compareTo(o.address);
        if (udpPort != o.udpPort) return udpPort - o.udpPort;
        if (tcpPort != o.tcpPort) return tcpPort - o.tcpPort;
        if (!nodeName.equals(o.nodeName)) return nodeName.compareTo(o.nodeName);
        if (healthy != o.healthy) return healthy ? 1 : -1;
        return 0;
    }

    @Override
    public String toString() {
        return "Node{" +
            "nodeName='" + nodeName + '\'' +
            ", address='" + address + '\'' +
            ", inetAddress=" + inetAddress +
            ", udpPort=" + udpPort +
            ", tcpPort=" + tcpPort +
            ", healthy=" + healthy +
            '}';
    }
}
