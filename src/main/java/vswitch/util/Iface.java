package vswitch.util;

import vproxy.util.Utils;

import java.net.InetSocketAddress;
import java.util.Objects;

public class Iface {
    public final InetSocketAddress udpSockAddress;

    public Iface(InetSocketAddress udpSockAddress) {
        this.udpSockAddress = udpSockAddress;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Iface iface = (Iface) o;
        return Objects.equals(udpSockAddress, iface.udpSockAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(udpSockAddress);
    }

    @Override
    public String toString() {
        return "Iface(" + Utils.l4addrStr(udpSockAddress) + ')';
    }
}
