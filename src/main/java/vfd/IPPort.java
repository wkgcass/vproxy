package vfd;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class IPPort extends SockAddr {
    private final IP ip;
    private final int port;

    public IPPort(IP ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public static IPPort fromNullable(SocketAddress sockAddr) {
        if (sockAddr == null) {
            return null;
        }
        return from(sockAddr);
    }

    public static IPPort from(SocketAddress sockAddr) {
        InetSocketAddress l4addr = (InetSocketAddress) sockAddr;
        return new IPPort(IP.from(l4addr.getAddress()), l4addr.getPort());
    }

    public InetSocketAddress toInetSocketAddress() {
        return new InetSocketAddress(ip.toInetAddress(), port);
    }

    public IP getAddress() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return ip + ":" + port;
    }
}
