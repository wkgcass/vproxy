package io.vproxy.vpacket.icmpv6;

import io.vproxy.base.util.ByteArray;
import io.vproxy.vfd.MacAddress;

import java.util.ArrayList;
import java.util.List;

/**
 * Common parts of the icmp body of a neighbor discovery message (RFC 4861):
 * the options tail. The body is the icmp packet content after the
 * 4-byte icmp header [type(1), code(1), checksum(2)].
 */
public abstract class AbstractNdpPacket {
    private final List<NdpOption> options = new ArrayList<>();

    /**
     * Parses the icmp body into this packet.
     * Returns null on success, or an error message.
     * Options are best-effort: parsing stops at the first malformed option.
     */
    public abstract String from(ByteArray bytes);

    /**
     * Returns the mac carried by the first ethernet link-layer-address option
     * of the given type (see Consts.ICMPv6_OPTION_TYPE_xxx), or null if absent.
     */
    public MacAddress getLinkLayerAddress(int optionType) {
        for (var opt : options) {
            if (opt.getType() != optionType) {
                continue;
            }
            if (opt.getData().length() != 6) {
                continue; // not an ethernet lladdr
            }
            return new MacAddress(opt.getData());
        }
        return null;
    }

    public List<NdpOption> getOptions() {
        return options;
    }

    protected void parseOptions(ByteArray bytes, int off) {
        options.addAll(NdpOption.parseAll(bytes, off));
    }

    protected ByteArray optionsToByteArray() {
        var ret = ByteArray.allocate(0);
        for (var opt : options) {
            ret = ret.concat(opt.toByteArray());
        }
        return ret;
    }
}
