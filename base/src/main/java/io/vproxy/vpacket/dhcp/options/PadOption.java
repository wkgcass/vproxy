package io.vproxy.vpacket.dhcp.options;

import io.vproxy.vpacket.dhcp.DHCPOption;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;

public class PadOption extends DHCPOption {
    public PadOption() {
        type = Consts.DHCP_OPT_TYPE_PAD;
    }

    @Override
    public ByteArray serialize() {
        return ByteArray.from(Consts.DHCP_OPT_TYPE_PAD);
    }

    @Override
    public int deserialize(ByteArray arr) throws Exception {
        if (arr.length() < 1) {
            throw new Exception("input too short for dhcp option (pad): cannot read type");
        }
        type = arr.get(0);
        return 1;
    }

    @Override
    public String toString() {
        return "PadOption{}";
    }
}
