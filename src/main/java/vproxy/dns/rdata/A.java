package vproxy.dns.rdata;

/*
 *     +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 *     |                    ADDRESS                    |
 *     +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 */

import vproxy.dns.DNSType;
import vproxy.dns.InvalidDNSPacketException;
import vproxy.util.ByteArray;
import vproxy.util.Logger;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

public class A implements RData {
    public Inet4Address address;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        A a = (A) o;
        return Objects.equals(address, a.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }

    @Override
    public String toString() {
        return "A{" +
            "address=" + address +
            '}';
    }

    @Override
    public ByteArray toByteArray() {
        return ByteArray.from(address.getAddress());
    }

    @Override
    public DNSType type() {
        return DNSType.A;
    }

    @Override
    public void fromByteArray(ByteArray data) throws InvalidDNSPacketException {
        if (data.length() != 4)
            throw new InvalidDNSPacketException("A record rdata length is not wrong: " + data.length());
        byte[] arr = data.toJavaArray();
        try {
            address = (Inet4Address) InetAddress.getByAddress(arr);
        } catch (UnknownHostException e) {
            Logger.shouldNotHappen("getting l3addr from ipv4 byte array failed", e);
        }
    }
}
