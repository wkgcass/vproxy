package vfd.posix;

import vproxy.util.Utils;

import java.net.InetSocketAddress;

public class SocketAddressIPv4 implements VSocketAddress {
    public final int ip;
    public final int port;

    public SocketAddressIPv4(int ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    @Override
    public InetSocketAddress toInetSocketAddress() {
        return new InetSocketAddress(Utils.l3addr(Utils.ipv4Int2Bytes(ip)), port);
    }

    @Override
    public String toString() {
        return "SocketAddressIPv4{" +
            "ip=" + Utils.ipStr(Utils.ipv4Int2Bytes(ip)) +
            ", port=" + port +
            '}';
    }
}
