package io.vproxy.vpacket.icmpv6;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.vfd.MacAddress;

/**
 * ICMPv6 Neighbor Advertisement message body (RFC 4861 4.4),
 * i.e. the icmp packet content after the 4-byte icmp header.
 * Layout: flags(4, only the first byte is used) + target address(16) + options.
 */
public class NeighborAdvertisementPacket extends AbstractNeighborDiscoveryPacket {
    public static final int FLAG_ROUTER = 0x80;
    public static final int FLAG_SOLICITED = 0x40;
    public static final int FLAG_OVERRIDE = 0x20;

    private int flags;

    @Override
    public String from(ByteArray bytes) {
        var err = super.from(bytes);
        if (err != null) {
            return err;
        }
        flags = bytes.uint8(0);
        return null;
    }

    public MacAddress getTargetLinkLayerAddress() {
        return getLinkLayerAddress(Consts.ICMPv6_OPTION_TYPE_Target_Link_Layer_Address);
    }

    public boolean isRouter() {
        return (flags & FLAG_ROUTER) != 0;
    }

    public void setRouter(boolean router) {
        flags = router ? flags | FLAG_ROUTER : flags & ~FLAG_ROUTER;
    }

    public boolean isSolicited() {
        return (flags & FLAG_SOLICITED) != 0;
    }

    public void setSolicited(boolean solicited) {
        flags = solicited ? flags | FLAG_SOLICITED : flags & ~FLAG_SOLICITED;
    }

    /**
     * When set, the target link-layer address should override a cached entry;
     * when clear, an existing entry with a different mac must not be replaced (RFC 4861 7.2.5).
     */
    public boolean isOverride() {
        return (flags & FLAG_OVERRIDE) != 0;
    }

    public void setOverride(boolean override) {
        flags = override ? flags | FLAG_OVERRIDE : flags & ~FLAG_OVERRIDE;
    }

    public ByteArray toByteArray() {
        var ret = targetAddressToByteArray().concat(optionsToByteArray());
        ret.set(0, (byte) flags);
        return ret;
    }

    @Override
    public String toString() {
        return "NeighborAdvertisementPacket{" +
            "router=" + isRouter() +
            ", solicited=" + isSolicited() +
            ", override=" + isOverride() +
            ", targetAddress=" + getTargetAddress() +
            ", options=" + getOptions() +
            '}';
    }
}
