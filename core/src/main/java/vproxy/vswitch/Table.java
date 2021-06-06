package vproxy.vswitch;

import vproxy.base.connection.NetEventLoop;
import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.util.Annotations;
import vproxy.base.util.Network;
import vproxy.base.util.exception.AlreadyExistException;
import vproxy.base.util.exception.XException;
import vproxy.vfd.IP;
import vproxy.vfd.MacAddress;
import vproxy.vpacket.conntrack.Conntrack;

public class Table {
    public final int vni;
    public final Network v4network;
    public final Network v6network;
    public final MacTable macTable;
    public final ArpTable arpTable;
    public final SyntheticIpHolder ips;
    public final ProxyHolder proxies;
    public final RouteTable routeTable;
    private Annotations annotations;

    public final Conntrack conntrack = new Conntrack();

    public Table(Switch sw, int vni, NetEventLoop loop,
                 Network v4network, Network v6network,
                 int macTableTimeout, int arpTableTimeout,
                 Annotations annotations) {
        this.vni = vni;
        this.v4network = v4network;
        this.v6network = v6network;
        if (annotations == null) {
            annotations = new Annotations();
        }
        this.annotations = annotations;

        macTable = new MacTable(loop.getSelectorEventLoop(), macTableTimeout);
        arpTable = new ArpTable(loop.getSelectorEventLoop(), arpTableTimeout);
        ips = new SyntheticIpHolder(this);
        proxies = new ProxyHolder(loop, sw, this);
        routeTable = new RouteTable(this);
    }

    public void setMacTableTimeout(int macTableTimeout) {
        macTable.setTimeout(macTableTimeout);
    }

    public void setArpTableTimeout(int arpTableTimeout) {
        arpTable.setTimeout(arpTableTimeout);
    }

    public void addIp(IP ip, MacAddress mac, Annotations annotations) throws AlreadyExistException, XException {
        ips.add(ip, mac, annotations);
    }

    public void clearCache() {
        macTable.clearCache();
        arpTable.clearCache();
    }

    public void setLoop(SelectorEventLoop loop) {
        macTable.setLoop(loop);
        arpTable.setLoop(loop);
    }

    public MacAddress lookup(IP ip) {
        var mac = arpTable.lookup(ip);
        if (mac == null) {
            mac = ips.lookup(ip);
        }
        return mac;
    }

    public Annotations getAnnotations() {
        return annotations;
    }

    public void setAnnotations(Annotations annotations) {
        if (annotations == null) {
            annotations = new Annotations();
        }
        this.annotations = annotations;
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
