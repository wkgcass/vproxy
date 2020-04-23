package vswitch.util;

import vproxy.util.Utils;

import java.net.InetSocketAddress;
import java.util.Objects;

public class Iface {
    public final String user;
    public final InetSocketAddress udpSockAddress;

    public Iface(InetSocketAddress udpSockAddress) {
        this.user = null;
        this.udpSockAddress = udpSockAddress;
    }

    public Iface(String user, InetSocketAddress udpSockAddress) {
        this.user = user;
        this.udpSockAddress = udpSockAddress;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Iface iface = (Iface) o;
        return Objects.equals(user, iface.user) &&
            Objects.equals(udpSockAddress, iface.udpSockAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, udpSockAddress);
    }

    @Override
    public String toString() {
        if (user == null) {
            return "Iface(" + Utils.l4addrStr(udpSockAddress) + ')';
        } else {
            return "Iface(" + user + ", " + Utils.l4addrStr(udpSockAddress) + ')';
        }
    }
}
