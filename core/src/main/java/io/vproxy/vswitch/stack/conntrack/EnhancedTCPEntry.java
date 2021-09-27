package vproxy.vswitch.stack.conntrack;

import vproxy.vfd.IPPort;
import vproxy.vpacket.conntrack.tcp.TcpEntry;
import vproxy.vpacket.conntrack.tcp.TcpListenEntry;

public class EnhancedTCPEntry extends TcpEntry {
    public Fastpath fastpath;

    public EnhancedTCPEntry(TcpListenEntry listenEntry, IPPort source, IPPort destination, long seq) {
        super(listenEntry, source, destination, seq);
    }
}
