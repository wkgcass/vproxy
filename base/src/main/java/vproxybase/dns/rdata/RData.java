package vproxybase.dns.rdata;

import vproxybase.dns.DNSType;
import vproxybase.dns.InvalidDNSPacketException;
import vproxybase.util.ByteArray;

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
            case PTR:
                return new PTR();
            default:
                return null;
        }
    }

    ByteArray toByteArray();

    DNSType type();

    void fromByteArray(ByteArray data, ByteArray rawPacket) throws InvalidDNSPacketException;
}
