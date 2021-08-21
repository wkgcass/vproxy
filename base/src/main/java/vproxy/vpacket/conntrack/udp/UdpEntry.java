package vproxy.vpacket.conntrack.udp;

import vproxy.vfd.IPPort;

public class UdpEntry {
    public final UdpListenEntry listenEntry;
    public final IPPort remote;
    public final IPPort local;

    private boolean destroyed = false;

    public Object userData;

    public UdpEntry(UdpListenEntry listenEntry,
                    IPPort remote, IPPort local) {
        this.listenEntry = listenEntry;
        this.remote = remote;
        this.local = local;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public void update() {
        // override this
    }

    public void destroy() {
        destroyed = true;
    }
}
