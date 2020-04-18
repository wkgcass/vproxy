package vproxy.util;

import java.net.InetAddress;
import java.util.Arrays;

public class Network {
    private final byte[] ip;
    private final byte[] mask;

    public Network(byte[] ip, byte[] mask) {
        this.ip = ip;
        this.mask = mask;
    }

    public boolean contains(InetAddress address) {
        return Utils.maskMatch(address.getAddress(), ip, mask);
    }

    public boolean contains(Network that) {
        if (!contains(Utils.l3addr(that.ip))) {
            return false;
        }
        return getMask() < that.getMask();
    }

    public String getIp() {
        return Utils.ipStr(ip);
    }

    public int getMask() {
        return Utils.maskInt(mask);
    }

    public byte[] getRawIpBytes() {
        return ip;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Network network = (Network) o;
        return Arrays.equals(ip, network.ip) &&
            Arrays.equals(mask, network.mask);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(ip);
        result = 31 * result + Arrays.hashCode(mask);
        return result;
    }

    @Override
    public String toString() {
        return getIp() + "/" + getMask();
    }
}
