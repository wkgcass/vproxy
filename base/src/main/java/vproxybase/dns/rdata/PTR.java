package vproxybase.dns.rdata;

import vproxybase.dns.DNSType;
import vproxybase.dns.Formatter;
import vproxybase.dns.InvalidDNSPacketException;
import vproxybase.util.ByteArray;

public class PTR implements RData {
    public String ptrdname;

    @Override
    public ByteArray toByteArray() {
        return Formatter.formatDomainName(ptrdname);
    }

    @Override
    public DNSType type() {
        return DNSType.PTR;
    }

    @Override
    public void fromByteArray(ByteArray data, ByteArray rawPacket) throws InvalidDNSPacketException {
        int[] offsetHolder = {0};
        String ptr = Formatter.parseDomainName(data, rawPacket, offsetHolder);
        if (offsetHolder[0] != data.length()) {
            throw new InvalidDNSPacketException("more bytes readable in the ptrdname rdata field: ptr=" + ptr + ", data.len=" + data.length());
        }
        this.ptrdname = ptr;
    }

    @Override
    public String toString() {
        return "PTR{" +
            "ptrdname='" + ptrdname + '\'' +
            '}';
    }
}
