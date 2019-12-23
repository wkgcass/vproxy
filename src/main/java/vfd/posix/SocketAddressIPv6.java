package vfd.posix;

import vproxy.util.Logger;
import vproxy.util.Utils;

import java.net.InetSocketAddress;

public class SocketAddressIPv6 implements VSocketAddress {
    public final String ip;
    public final int port;

    public SocketAddressIPv6(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    @Override
    public InetSocketAddress toInetSocketAddress() {
        byte[] ip = Utils.parseIpString(this.ip);
        if (ip == null) {
            Logger.shouldNotHappen("parse ipv6 from bytes " + this.ip + " failed");
            throw new RuntimeException("parse ip " + this.ip + " failed");
        }
        return new InetSocketAddress(Utils.l3addr(ip), port);
    }

    @Override
    public String toString() {
        return "SocketAddressIPv6{" +
            "ip=" + ip +
            ", port=" + port +
            '}';
    }
}
