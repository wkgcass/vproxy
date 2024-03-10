package io.vproxy.vfd;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Objects;

public class IPPort extends SockAddr {
    private final IP ip;
    private final int port;

    public IPPort(int port) {
        this(IP.from("0.0.0.0"), port);
    }

    public IPPort(String ip, int port) {
        this(IP.from(ip), port);
    }

    public IPPort(IP ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public IPPort(String ipport) {
        if (!validL4AddrStr(ipport)) {
            throw new IllegalArgumentException("input is not a valid ipport string");
        }
        int port = Integer.parseInt(ipport.substring(ipport.lastIndexOf(':') + 1));
        String ip = ipport.substring(0, ipport.lastIndexOf(':'));
        this.ip = IP.from(ip);
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
        if (l4addr instanceof UnixDomainSocketAddress) {
            return ((UnixDomainSocketAddress) l4addr).path;
        }
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

    public String formatToIPPortString() {
        return ip.formatToIPString() + ":" + port;
    }

    @Override
    public String toString() {
        return ip + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IPPort ipPort = (IPPort) o;
        return port == ipPort.port &&
            Objects.equals(ip, ipPort.ip);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ip, port);
    }

    public boolean ipportEquals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IPPort ipPort = (IPPort) o;
        return port == ipPort.port &&
               ip.ipEquals(ipPort.ip);
    }

    // BEGIN UTILS:

    private static final IPPort BIND_ANY_ADDRESS = new IPPort(IP.from("0.0.0.0"), 0);

    public static IPPort bindAnyAddress() {
        return BIND_ANY_ADDRESS;
    }

    public static boolean validL4AddrStr(String l4addr) {
        if (!l4addr.contains(":")) {
            return false;
        }
        String portStr = l4addr.substring(l4addr.lastIndexOf(":") + 1);
        String l3addr = l4addr.substring(0, l4addr.lastIndexOf(":"));
        if (IP.parseIpString(l3addr) == null) {
            return false;
        }
        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            return false;
        }
        //noinspection RedundantIfStatement
        if (port < 0 || port > 65535) {
            return false;
        }
        return true;
    }
}
