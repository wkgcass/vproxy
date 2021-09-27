package io.vproxy.vpacket.conntrack.udp;

import io.vproxy.base.util.ByteArray;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;

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
