package vfd.posix;

import vproxy.util.Logger;
import vproxy.util.Utils;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public class SocketAddressIPv4 implements VSocketAddress {
    public final int ip;
    public final int port;

    public SocketAddressIPv4(int ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    @Override
    public InetSocketAddress toInetSocketAddress() {
        try {
            return new InetSocketAddress(
                InetAddress.getByAddress(Utils.ipv4Int2Bytes(ip)),
                port);
        } catch (UnknownHostException e) {
            Logger.shouldNotHappen("parse ipv4 from int " + ip + " failed", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return "SocketAddressIPv4{" +
            "ip=" + Utils.ipStr(Utils.ipv4Int2Bytes(ip)) +
            ", port=" + port +
            '}';
    }
}
