package io.vproxy.vswitch.stack.conntrack;

import io.vproxy.vfd.IPPort;
import io.vproxy.vpacket.conntrack.Conntrack;
import io.vproxy.vpacket.conntrack.udp.UdpEntry;
import io.vproxy.vpacket.conntrack.udp.UdpListenEntry;

public class EnhancedUDPEntry extends UdpEntry {
    public Fastpath fastpath;

    public EnhancedUDPEntry(IPPort remote, IPPort local) {
        super(remote, local);
    }

    public EnhancedUDPEntry(UdpListenEntry listenEntry, IPPort remote, IPPort local,
                            Conntrack conntrack, int timeout) {
        super(listenEntry, remote, local, conntrack, timeout);
    }
}
