package vswitch.util;

import vproxy.util.Utils;

import java.net.InetSocketAddress;
import java.util.Objects;

public class Iface {
    public final String user;
    public final InetSocketAddress udpSockAddress;

    // the following should not be put into equals/hashCode
    public int clientSideVni;
    public int serverSideVni;

    public Iface(InetSocketAddress udpSockAddress) {
        this.user = null;
        this.udpSockAddress = udpSockAddress;
    }

    public Iface(InetSocketAddress udpSockAddress, String user) {
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
        // should not consider vni
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, udpSockAddress);
        // should not consider vni
    }

    @Override
    public String toString() {
        String vniStr;
        if (clientSideVni == 0) {
            vniStr = "";
        } else {
            vniStr = Utils.toHexStringWithPadding(clientSideVni, 24) + ",";
        }
        String userStr;
        if (user == null) {
            userStr = "";
        } else {
            userStr = "," + user.replace(Consts.USER_PADDING, "");
        }
        return "Iface(" + vniStr + Utils.l4addrStr(udpSockAddress) + userStr + ')';
    }
}
