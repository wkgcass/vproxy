package vproxy.vswitch.stack.conntrack;

import vproxy.vfd.IPPort;
import vproxy.vpacket.conntrack.Conntrack;
import vproxy.vpacket.conntrack.tcp.TcpEntry;
import vproxy.vpacket.conntrack.tcp.TcpListenEntry;

public class EnhancedConntrack extends Conntrack {
    @Override
    protected TcpEntry createTcpEntry(TcpListenEntry listenEntry, IPPort src, IPPort dst, long seq) {
        return new EnhancedTCPEntry(listenEntry, src, dst, seq);
    }
}
