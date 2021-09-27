package io.vproxy.vfd.posix;

import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;

public class SocketAddressIPv4 implements VSocketAddress {
    public final int ip;
    public final int port;

    public SocketAddressIPv4(int ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    @Override
    public IPPort toIPPort() {
        return new IPPort(IP.from(IP.ipv4Int2Bytes(ip)), port);
    }

    @Override
    public String toString() {
        return "SocketAddressIPv4{" +
            "ip=" + IP.ipStr(IP.ipv4Int2Bytes(ip)) +
            ", port=" + port +
            '}';
    }
}
