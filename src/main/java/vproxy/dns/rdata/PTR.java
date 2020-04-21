package vproxy.dns.rdata;

import vproxy.dns.DNSType;
import vproxy.dns.Formatter;
import vproxy.dns.InvalidDNSPacketException;
import vproxy.util.ByteArray;

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
