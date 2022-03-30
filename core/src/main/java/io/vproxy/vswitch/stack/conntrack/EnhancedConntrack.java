package io.vproxy.vswitch.stack.conntrack;

import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.vfd.IPPort;
import io.vproxy.vpacket.conntrack.Conntrack;
import io.vproxy.vpacket.conntrack.tcp.TcpEntry;
import io.vproxy.vpacket.conntrack.tcp.TcpListenEntry;

public class EnhancedConntrack extends Conntrack {
    public EnhancedConntrack(SelectorEventLoop loop) {
        super(loop);
    }

    @Override
    protected TcpEntry createTcpEntry(TcpListenEntry listenEntry, IPPort remote, IPPort local, long seq) {
        return new EnhancedTCPEntry(listenEntry, remote, local, seq);
    }

    @Override
    protected TcpEntry createTcpEntry(IPPort remote, IPPort local) {
        return new EnhancedTCPEntry(remote, local);
    }
}
