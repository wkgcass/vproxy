package vproxy.vfd.posix;

import vproxy.base.util.Logger;
import vproxy.vfd.IP;
import vproxy.vfd.IPPort;

public class SocketAddressIPv6 implements VSocketAddress {
    public final String ip;
    public final int port;

    public SocketAddressIPv6(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    @Override
    public IPPort toIPPort() {
        byte[] ip = IP.parseIpString(this.ip);
        if (ip == null) {
            Logger.shouldNotHappen("parse ipv6 from bytes " + this.ip + " failed");
            throw new RuntimeException("parse ip " + this.ip + " failed");
        }
        return new IPPort(IP.from(ip), port);
    }

    @Override
    public String toString() {
        return "SocketAddressIPv6{" +
            "ip=" + ip +
            ", port=" + port +
            '}';
    }
}
