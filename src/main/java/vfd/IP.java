package vfd;

import vproxy.util.Utils;

import java.net.InetAddress;

public abstract class IP {
    public static IP from(InetAddress ip) {
        return from(ip.getAddress());
    }

    public static IP from(byte[] arr) {
        if (arr.length == 4) {
            return new IPv4(arr);
        } else if (arr.length == 16) {
            return new IPv6(arr);
        } else {
            throw new IllegalArgumentException("unknown ip address");
        }
    }

    public static IP from(String ip) {
        byte[] bytes = Utils.parseIpString(ip);
        if (bytes == null) {
            throw new IllegalArgumentException("input is not a valid ip string");
        }
        return from(bytes);
    }

    public static IPv4 fromIPv4(byte[] bytes) {
        if (bytes.length != 4) {
            throw new IllegalArgumentException("input is not a valid ipv4 address");
        }
        return new IPv4(bytes);
    }

    public static IPv4 fromIPv4(String ip) {
        byte[] bytes = Utils.parseIpv4String(ip);
        if (bytes == null) {
            throw new IllegalArgumentException("input is not a valid ipv4 string");
        }
        return fromIPv4(bytes);
    }

    public static IPv6 fromIPv6(byte[] bytes) {
        if (bytes.length != 16) {
            throw new IllegalArgumentException("input is not a valid ipv6 address");
        }
        return new IPv6(bytes);
    }

    public static IPv6 fromIPv6(String ip) {
        byte[] bytes = Utils.parseIpv6String(ip);
        if (bytes == null) {
            throw new IllegalArgumentException("input is not a valid ipv6 string");
        }
        return fromIPv6(bytes);
    }

    private final byte[] bytes;

    IP(byte[] bytes) {
        this.bytes = bytes;
    }

    public byte[] getAddress() {
        byte[] ret = new byte[bytes.length];
        System.arraycopy(bytes, 0, ret, 0, ret.length);
        return ret;
    }

    public InetAddress toInetAddress() {
        return Utils.l3addr(getAddress());
    }

    public String formatToIpString() {
        return Utils.ipStr(bytes);
    }

    @Override
    public String toString() {
        return "/" + formatToIpString(); // compatible with java InetAddress
    }
}
