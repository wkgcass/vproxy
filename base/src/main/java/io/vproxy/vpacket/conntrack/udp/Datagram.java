package vproxy.vpacket.conntrack.udp;

import vproxy.base.util.ByteArray;
import vproxy.vfd.IP;
import vproxy.vfd.IPPort;

public class Datagram {
    public final IP remoteIp;
    public final int remotePort;
    public final ByteArray data;

    public Datagram(IPPort remote, ByteArray data) {
        this(remote.getAddress(), remote.getPort(), data);
    }

    public Datagram(IP remoteIp, int remotePort, ByteArray data) {
        this.remoteIp = remoteIp;
        this.remotePort = remotePort;
        this.data = data;
    }
}
