package vswitch.packet;

import vproxy.util.ByteArray;
import vproxy.util.Utils;
import vswitch.util.Consts;
import vswitch.util.MacAddress;

public class EthernetPacket extends AbstractEthernetPacket {
    public MacAddress dst;
    public MacAddress src;
    public int type;
    public AbstractPacket packet;

    @Override
    public String from(ByteArray bytes) {
        if (bytes.length() < (6 /*dst*/ + 6 /*src*/ + 2 /*type*/)) {
            return "input packet length too short for a ethernet packet";
        }
        dst = new MacAddress(bytes.sub(0, 6));
        src = new MacAddress(bytes.sub(6, 6));
        type = bytes.uint16(12);
        ByteArray data = bytes.sub(14, bytes.length() - 14);
        AbstractPacket packet;
        if (type == Consts.ETHER_TYPE_ARP) {
            packet = new ArpPacket();
        } else {
            packet = new PacketBytes();
        }
        String err = packet.from(data);
        if (err != null) {
            return err;
        }
        this.packet = packet;

        raw = bytes;
        return null;
    }

    @Override
    public String toString() {
        return "EthernetPacket{" +
            "dst=" + dst +
            ", src=" + src +
            ", type=" + Utils.toHexString(type) +
            ", packet=" + packet +
            '}';
    }

    @Override
    public MacAddress getSrc() {
        return src;
    }

    @Override
    public MacAddress getDst() {
        return dst;
    }

    @Override
    public AbstractPacket getPacket() {
        return packet;
    }
}
