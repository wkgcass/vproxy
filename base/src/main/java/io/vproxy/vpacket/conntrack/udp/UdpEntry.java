package io.vproxy.vpacket.conntrack.udp;

import io.vproxy.base.Config;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Timer;
import io.vproxy.vfd.IPPort;
import io.vproxy.vpacket.conntrack.Conntrack;

public class UdpEntry {
    public final UdpListenEntry listenEntry;
    public final IPPort remote;
    public final IPPort local;

    private boolean destroyed = false;

    public Object userData;

    protected final Timer timer;
    private final Conntrack conntrack;

    public UdpEntry(UdpListenEntry listenEntry,
                    IPPort remote, IPPort local,
                    Conntrack conntrack) {
        this(listenEntry, remote, local, conntrack, -1);
    }

    public UdpEntry(UdpListenEntry listenEntry,
                    IPPort remote, IPPort local,
                    Conntrack conntrack, int timeout) {
        this.listenEntry = listenEntry;
        this.remote = remote;
        this.local = local;
        this.conntrack = conntrack;

        if (timeout == -1) {
            this.timer = null;
        } else {
            this.timer = new Timer(conntrack.loop, Config.udpTimeout) {
                @Override
                public void cancel() {
                    assert Logger.lowLevelDebug("udp entry " + remote + "/" + local + " canceled");
                    super.cancel();
                    destroy();
                }
            };
            this.timer.start();
        }
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public void update() {
        if (isDestroyed()) {
            Logger.error(LogType.IMPROPER_USE, "calling update on UDPEntry while it's destroyed");
            return;
        }
        if (timer != null) {
            timer.resetTimer();
        }
    }

    public void destroy() {
        if (isDestroyed()) {
            return;
        }
        destroyed = true;

        if (timer != null) {
            timer.cancel();
        }
        conntrack.removeUdp(remote, local);
    }
}
