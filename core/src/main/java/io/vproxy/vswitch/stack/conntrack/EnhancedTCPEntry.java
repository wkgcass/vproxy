package io.vproxy.vswitch.stack.conntrack;

import io.vproxy.vpacket.conntrack.tcp.TcpEntry;
import io.vproxy.vpacket.conntrack.tcp.TcpListenEntry;
import io.vproxy.vfd.IPPort;
import io.vproxy.vpacket.conntrack.tcp.TcpEntry;
import io.vproxy.vpacket.conntrack.tcp.TcpListenEntry;

public class EnhancedTCPEntry extends TcpEntry {
    public Fastpath fastpath;

    public EnhancedTCPEntry(TcpListenEntry listenEntry, IPPort source, IPPort destination, long seq) {
        super(listenEntry, source, destination, seq);
    }
}
