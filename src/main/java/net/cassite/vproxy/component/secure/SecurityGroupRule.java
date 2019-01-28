package net.cassite.vproxy.component.secure;

import net.cassite.vproxy.connection.Protocol;
import net.cassite.vproxy.util.Utils;

import java.net.InetAddress;
import java.util.Arrays;

public class SecurityGroupRule {
    public final String alias;
    public final byte[] ip;
    public final byte[] mask;
    public final Protocol protocol;
    public final int minPort;
    public final int maxPort;
    public final boolean allow;

    public SecurityGroupRule(String alias,
                             byte[] ip, byte[] mask,
                             Protocol protocol, int minPort, int maxPort,
                             boolean allow) {
        this.alias = alias;
        this.ip = ip;
        this.mask = mask;
        this.protocol = protocol;
        this.minPort = minPort;
        this.maxPort = maxPort;
        this.allow = allow;
    }

    public boolean match(InetAddress address, int port) {
        return Utils.maskMatch(address.getAddress(), ip, mask) && minPort <= port && port <= maxPort;
    }

    public boolean ipMaskMatch(SecurityGroupRule rule) {
        return Arrays.equals(ip, rule.ip) && Arrays.equals(mask, rule.mask);
    }

    @Override
    public String toString() {
        return alias + " -> " + (allow ? "allow" : "deny") + " " + Utils.ipStr(ip) + "/" + Utils.maskInt(mask) +
            " protocol " + protocol +
            " port [" + minPort + "," + maxPort + "]";
    }
}
