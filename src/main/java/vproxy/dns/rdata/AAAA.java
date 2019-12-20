package vproxy.dns.rdata;

import vproxy.dns.DNSType;
import vproxy.dns.InvalidDNSPacketException;
import vproxy.util.ByteArray;
import vproxy.util.Logger;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

public class AAAA implements RData {
    public Inet6Address address;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AAAA aaaa = (AAAA) o;
        return Objects.equals(address, aaaa.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }

    @Override
    public String toString() {
        return "AAAA{" +
            "address=" + address +
            '}';
    }

    @Override
    public ByteArray toByteArray() {
        return ByteArray.from(address.getAddress());
    }

    @Override
    public DNSType type() {
        return DNSType.AAAA;
    }

    @Override
    public void fromByteArray(ByteArray data, ByteArray rawPacket) throws InvalidDNSPacketException {
        if (data.length() != 16)
            throw new InvalidDNSPacketException("AAAA record rdata length is not wrong: " + data.length());
        byte[] arr = data.toJavaArray();
        try {
            address = (Inet6Address) InetAddress.getByAddress(arr);
        } catch (UnknownHostException e) {
            Logger.shouldNotHappen("getting l3addr from ipv6 byte array failed", e);
        }
    }
}
