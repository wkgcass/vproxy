package io.vproxy.vpacket.icmpv6;

import io.vproxy.base.util.ByteArray;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPv6;

/**
 * Common parts of a neighbor discovery message carrying a target address
 * (neighbor solicitation RFC 4861 4.3 and neighbor advertisement RFC 4861 4.4).
 * Body layout: reserved-or-flags(4) + target address(16) + options.
 */
public abstract class AbstractNeighborDiscoveryPacket extends AbstractNdpPacket {
    private IPv6 targetAddress;

    @Override
    public String from(ByteArray bytes) {
        if (bytes.length() < 20) { // 4 reserved/flags and 16 target address
            return "input length too short for a neighbor discovery message";
        }
        targetAddress = IP.fromIPv6(bytes.sub(4, 16).toJavaArray());
        parseOptions(bytes, 20);
        return null;
    }

    public IPv6 getTargetAddress() {
        return targetAddress;
    }

    public void setTargetAddress(IPv6 targetAddress) {
        this.targetAddress = targetAddress;
    }

    protected ByteArray targetAddressToByteArray() {
        var ret = ByteArray.allocate(20);
        var addr = targetAddress.bytes;
        for (int i = 0; i < 16; ++i) {
            ret.set(4 + i, addr.get(i));
        }
        return ret;
    }
}
