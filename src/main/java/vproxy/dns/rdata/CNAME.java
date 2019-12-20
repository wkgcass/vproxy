package vproxy.dns.rdata;

/*
 *     +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 *     /                     CNAME                     /
 *     /                                               /
 *     +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 */

import vproxy.dns.DNSType;
import vproxy.dns.Formatter;
import vproxy.dns.InvalidDNSPacketException;
import vproxy.util.ByteArray;

import java.util.Objects;

public class CNAME implements RData {
    public String cname;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CNAME cname1 = (CNAME) o;
        return Objects.equals(cname, cname1.cname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cname);
    }

    @Override
    public String toString() {
        return "CNAME{" +
            "cname='" + cname + '\'' +
            '}';
    }

    @Override
    public ByteArray toByteArray() {
        return Formatter.formatDomainName(cname);
    }

    @Override
    public DNSType type() {
        return DNSType.CNAME;
    }

    @Override
    public void fromByteArray(ByteArray data, ByteArray rawPacket) throws InvalidDNSPacketException {
        int[] offsetHolder = {0};
        String cname = Formatter.parseDomainName(data, rawPacket, offsetHolder);
        if (offsetHolder[0] != data.length()) {
            throw new InvalidDNSPacketException("more bytes readable in the cname rdata field: cname=" + cname + ", data.len=" + data.length());
        }
        this.cname = cname;
    }
}
