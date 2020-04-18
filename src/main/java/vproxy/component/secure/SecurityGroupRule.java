package vproxy.component.secure;

import vproxy.connection.Protocol;
import vproxy.util.Network;

import java.net.InetAddress;

public class SecurityGroupRule {
    public final String alias;
    public final Network network;
    public final Protocol protocol;
    public final int minPort;
    public final int maxPort;
    public final boolean allow;

    public SecurityGroupRule(String alias,
                             byte[] ip, byte[] mask,
                             Protocol protocol, int minPort, int maxPort,
                             boolean allow) {
        this.alias = alias;
        this.network = new Network(ip, mask);
        this.protocol = protocol;
        this.minPort = minPort;
        this.maxPort = maxPort;
        this.allow = allow;
    }

    public boolean match(InetAddress address, int port) {
        return network.contains(address) && minPort <= port && port <= maxPort;
    }

    public boolean ipMaskMatch(SecurityGroupRule rule) {
        return this.network.equals(rule.network);
    }

    @Override
    public String toString() {
        return alias + " -> " + (allow ? "allow" : "deny") + " " + network +
            " protocol " + protocol +
            " port [" + minPort + "," + maxPort + "]";
    }
}
