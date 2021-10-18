package io.vproxy.base.util;

import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPv4;
import io.vproxy.vfd.IPv6;

public class NetworkV4 extends Network {
    private final int ipv4;

    protected NetworkV4(IPv4 ip, ByteArray mask) {
        super(ip, mask);
        ipv4 = ip.getIPv4Value();
    }

    @Override
    public boolean contains(IP address) {
        if (address instanceof IPv4) {
            return (((IPv4) address).getIPv4Value() & Utils.maskNumberToInt(getMask())) == ipv4;
        } else if (address instanceof IPv6) {
            IPv6 v6 = (IPv6) address;
            if (v6.isV4MappedV6Address() || v6.isV4CompatibleV6Address()) {
                return (v6.getIPv6Value3() & Utils.maskNumberToInt(getMask())) == ipv4;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
}
