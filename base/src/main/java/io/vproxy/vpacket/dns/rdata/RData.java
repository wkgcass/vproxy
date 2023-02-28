package io.vproxy.vpacket.dns.rdata;

import io.vproxy.vpacket.dns.DNSType;
import io.vproxy.vpacket.dns.InvalidDNSPacketException;
import io.vproxy.base.util.ByteArray;

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
