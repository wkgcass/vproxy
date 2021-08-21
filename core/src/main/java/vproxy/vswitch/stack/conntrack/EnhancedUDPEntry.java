package vproxy.vswitch.stack.conntrack;

import vproxy.base.Config;
import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.util.LogType;
import vproxy.base.util.Logger;
import vproxy.base.util.Timer;
import vproxy.vfd.IPPort;
import vproxy.vpacket.conntrack.udp.UdpEntry;
import vproxy.vpacket.conntrack.udp.UdpListenEntry;
import vproxy.vswitch.VirtualNetwork;

public class EnhancedUDPEntry extends UdpEntry {
    private final Timer timer;
    private final VirtualNetwork network;
    public Fastpath fastpath;

    public EnhancedUDPEntry(UdpListenEntry listenEntry, IPPort remote, IPPort local,
                            VirtualNetwork network, SelectorEventLoop loop) {
        super(listenEntry, remote, local);
        this.network = network;
        this.timer = new Timer(loop, Config.udpTimeout) {
            @Override
            public void cancel() {
                assert Logger.lowLevelDebug("udp entry " + remote + "/" + local + " canceled");
                super.cancel();
                destroy();
            }
        };
    }

    @Override
    public void update() {
        if (isDestroyed()) {
            Logger.error(LogType.IMPROPER_USE, "calling update on UDPEntry while it's destroyed");
            return;
        }
        timer.resetTimer();
    }

    @Override
    public void destroy() {
        if (isDestroyed()) {
            return;
        }
        super.destroy();
        timer.cancel();
        network.conntrack.removeUdp(remote, local);
    }
}
