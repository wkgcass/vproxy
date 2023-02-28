package io.vproxy.vpacket.dns.rdata;

import io.vproxy.vpacket.dns.DNSType;
import io.vproxy.vpacket.dns.InvalidDNSPacketException;
import io.vproxy.base.util.ByteArray;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPv6;

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
