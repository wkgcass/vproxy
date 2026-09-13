package io.vproxy.vpacket.icmpv6;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.vfd.MacAddress;

/**
 * ICMPv6 Neighbor Solicitation message body (RFC 4861 4.3),
 * i.e. the icmp packet content after the 4-byte icmp header.
 * Layout: reserved(4) + target address(16) + options.
 */
public class NeighborSolicitationPacket extends AbstractNeighborDiscoveryPacket {
    // from() is inherited: the reserved field carries no information

    public MacAddress getSourceLinkLayerAddress() {
        return getLinkLayerAddress(Consts.ICMPv6_OPTION_TYPE_Source_Link_Layer_Address);
    }

    public ByteArray toByteArray() {
        return targetAddressToByteArray().concat(optionsToByteArray());
    }

    @Override
    public String toString() {
        return "NeighborSolicitationPacket{" +
            "targetAddress=" + getTargetAddress() +
            ", options=" + getOptions() +
            '}';
    }
}
