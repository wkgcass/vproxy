package vproxy.base.dns.rdata;

import vproxy.base.dns.DNSType;
import vproxy.base.dns.InvalidDNSPacketException;
import vproxy.base.util.ByteArray;
import vproxy.vfd.IP;
import vproxy.vfd.IPv6;

import java.util.Objects;

public class AAAA implements RData {
    public IPv6 address;

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
        address = IP.fromIPv6(arr);
    }
}
