package vproxy.dns.rdata;

import vproxy.dns.DNSType;
import vproxy.dns.InvalidDNSPacketException;
import vproxy.util.ByteArray;

public interface RData {
    static RData newRData(DNSType type) {
        switch (type) {
            case A:
                return new A();
            case AAAA:
                return new AAAA();
            case CNAME:
                return new CNAME();
            case TXT:
                return new TXT();
            case SRV:
                return new SRV();
            default:
                return null;
        }
    }

    ByteArray toByteArray();

    DNSType type();

    void fromByteArray(ByteArray data, ByteArray rawPacket) throws InvalidDNSPacketException;
}
