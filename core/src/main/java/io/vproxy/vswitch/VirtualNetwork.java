package io.vproxy.vswitch;

import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.Annotations;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Network;
import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.base.util.exception.XException;
import io.vproxy.vfd.*;
import io.vproxy.vpacket.conntrack.Conntrack;
import io.vproxy.vpacket.conntrack.udp.UdpListenEntry;
import io.vproxy.vswitch.stack.conntrack.EnhancedConntrack;
import io.vproxy.vswitch.stack.fd.VSwitchFDContext;
import io.vproxy.vswitch.stack.fd.VSwitchFDs;

import java.util.concurrent.ThreadLocalRandom;

public class VirtualNetwork {
    public final int vni;
    public final Network v4network;
    public final Network v6network;
    public final MacTable macTable;
    public final ArpTable arpTable;
    public final SyntheticIpHolder ips;
    public final RouteTable routeTable;
    private Annotations annotations;

    public final Conntrack conntrack = new EnhancedConntrack();

    private final FDs fds;

    public VirtualNetwork(SwitchContext swCtx,
                          int vni, NetEventLoop loop,
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
        routeTable = new RouteTable(this);

        this.fds = new VSwitchFDs(new VSwitchFDContext(swCtx, this));
    }

    public void setMacTableTimeout(int macTableTimeout) {
        macTable.setTimeout(macTableTimeout);
    }

    public void setArpTableTimeout(int arpTableTimeout) {
        arpTable.setTimeout(arpTableTimeout);
    }

    public IPMac addIp(IP ip, MacAddress mac, Annotations annotations) throws AlreadyExistException, XException {
        return ips.add(ip, mac, annotations);
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

    private static final int IP_LOCAL_PORT_MIN = 30720;
    private static final int IP_LOCAL_PORT_MAX = 32768;

    public IPPort findFreeUdpIPPort(IPPort remote) {
        for (var ip : ips.allRoutableIps()) {
            if (remote.getAddress() instanceof IPv4) {
                if (!(ip instanceof IPv4)) continue;
            } else {
                if (!(ip instanceof IPv6)) continue;
            }
            IPPort ipport = findFreeUdpIPPort(ip);
            if (ipport != null) {
                return ipport;
            }
        }
        return null;
    }

    private IPPort findFreeUdpIPPort(IP ip) {
        for (int i = 0; i < 100; ++i) { // randomly retry 100 times
            int port = ThreadLocalRandom.current().nextInt(IP_LOCAL_PORT_MAX - IP_LOCAL_PORT_MIN) + IP_LOCAL_PORT_MIN;
            var ipport = new IPPort(ip, port);
            UdpListenEntry entry = conntrack.lookupUdpListen(ipport);
            if (entry == null) {
                return ipport;
            }
        }
        assert Logger.lowLevelDebug("unable to allocate a free port for udp from " + ip);
        return null;
    }

    public FDs fds() {
        return fds;
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
        return "VirtualNetwork{" +
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
