package vswitch.util;

import vfd.posix.TunTapDatagramFD;
import vproxy.util.Utils;

import java.net.InetSocketAddress;
import java.util.Objects;

public class Iface {
    public final String user; // null for bare vxlan sock

    public final InetSocketAddress udpSockAddress; // for vxlan or vproxy wrapped vxlan
    public final TunTapDatagramFD tap; // for a tap device

    // the following should not be put into equals/hashCode
    public int clientSideVni;
    public int serverSideVni;

    public Iface(InetSocketAddress udpSockAddress) {
        this.user = null;
        this.udpSockAddress = udpSockAddress;
        this.tap = null;
    }

    public Iface(InetSocketAddress udpSockAddress, String user) {
        this.user = user;
        this.udpSockAddress = udpSockAddress;
        this.tap = null;
    }

    public Iface(TunTapDatagramFD tap) {
        this.user = null;
        this.udpSockAddress = null;
        this.tap = tap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Iface iface = (Iface) o;
        return Objects.equals(user, iface.user) &&
            Objects.equals(udpSockAddress, iface.udpSockAddress) &&
            Objects.equals(tap, iface.tap);
        // should not consider vni
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, udpSockAddress, tap);
        // should not consider vni
    }

    @Override
    public String toString() {
        String userStr;
        if (user == null) {
            userStr = "";
        } else {
            userStr = "," + user.replace(Consts.USER_PADDING, "");
        }
        if (udpSockAddress != null) {
            return "Iface(" + Utils.l4addrStr(udpSockAddress) + userStr + ')';
        } else {
            assert tap != null;
            return "Iface(" + tap.tuntap.dev + "," + serverSideVni + ")";
        }
    }
}
