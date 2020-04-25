package vswitch;

import vproxy.component.exception.AlreadyExistException;
import vproxy.component.exception.XException;
import vproxy.selector.SelectorEventLoop;
import vproxy.util.Network;
import vproxy.util.Utils;
import vswitch.util.MacAddress;

import java.net.Inet4Address;
import java.net.InetAddress;

public class Table {
    public final int vni;
    public final Network v4network;
    public final Network v6network;
    public final MacTable macTable;
    public final ArpTable arpTable;
    public final SyntheticIpHolder ips;
    public final RouteTable routeTable;

    public Table(int vni, SelectorEventLoop loop,
                 Network v4network, Network v6network,
                 int macTableTimeout, int arpTableTimeout) {
        this.vni = vni;
        this.v4network = v4network;
        this.v6network = v6network;

        macTable = new MacTable(loop, macTableTimeout);
        arpTable = new ArpTable(loop, arpTableTimeout);
        ips = new SyntheticIpHolder();
        routeTable = new RouteTable();
    }

    public void setMacTableTimeout(int macTableTimeout) {
        macTable.setTimeout(macTableTimeout);
    }

    public void setArpTableTimeout(int arpTableTimeout) {
        arpTable.setTimeout(arpTableTimeout);
    }

    public void addIp(InetAddress ip, MacAddress mac) throws AlreadyExistException, XException {
        if (ip instanceof Inet4Address) {
            if (!v4network.contains(ip)) {
                throw new XException("the ip to add (" + Utils.ipStr(ip) + ") is not in the allowed range " + v4network);
            }
        } else {
            if (v6network == null) {
                throw new XException("ipv6 not allowed");
            }
            if (!v6network.contains(ip)) {
                throw new XException("the ip to add (" + Utils.ipStr(ip) + ") is not in the allowed range " + v6network);
            }
        }
        ips.add(ip, mac);
    }

    public void clearCache() {
        macTable.clearCache();
        arpTable.clearCache();
    }

    public void setLoop(SelectorEventLoop loop) {
        macTable.setLoop(loop);
        arpTable.setLoop(loop);
    }

    @Override
    public String toString() {
        return "Table{" +
            "vni=" + vni +
            ", v4network=" + v4network +
            ", v6network=" + v6network +
            ", macTable=" + macTable +
            ", arpTable=" + arpTable +
            ", ips=" + ips +
            ", routeTable=" + routeTable +
            '}';
    }
}
