package vfd.posix;

import vproxy.util.Logger;
import vproxy.util.Utils;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

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
        try {
            return new InetSocketAddress(
                InetAddress.getByAddress(ip),
                port);
        } catch (UnknownHostException e) {
            Logger.shouldNotHappen("parse ipv6 from bytes " + Arrays.toString(ip) + " failed", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return "SocketAddressIPv6{" +
            "ip=" + ip +
            ", port=" + port +
            '}';
    }
}
