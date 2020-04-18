package vswitch.packet;

import vproxy.util.ByteArray;
import vproxy.util.Utils;

import static vproxy.util.Utils.runAvoidNull;

public class VXLanPacket extends AbstractPacket {
    public int flags;
    public int vni;
    public AbstractEthernetPacket packet;

    @Override
    public String from(ByteArray bytes) {
        if (bytes.length() < 1 + 3 + 3 + 1) {
            return "input packet length too short for a vxlan packet";
        }
        flags = bytes.uint8(0);
        vni = bytes.uint24(4);
        // for now, we only consider it being this type of ethernet packet
        packet = new EthernetPacket();
        String err = packet.from(bytes.sub(8, bytes.length() - 8));
        if (err != null) {
            return err;
        }

        raw = bytes;
        return null;
    }

    @Override
    public String toString() {
        return "VXLanPacket{" +
            "flags=" + Utils.toBinaryString(flags) +
            ", vni=" + runAvoidNull(() -> vni, "null") +
            ", packet=" + packet +
            '}';
    }
}
