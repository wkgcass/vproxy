package vswitch.packet;

import vproxy.util.ByteArray;
import vproxy.util.Utils;

import java.util.Objects;

import static vproxy.util.Utils.runAvoidNull;

public class VXLanPacket extends AbstractPacket {
    private int flags;
    private int vni;
    private AbstractEthernetPacket packet;

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
    protected ByteArray buildPacket() {
        return ByteArray.allocate(8).set(0, (byte) flags).int24(4, vni).concat(packet.getRawPacket());
    }

    @Override
    public String toString() {
        return "VXLanPacket{" +
            "flags=" + Utils.toBinaryString(flags) +
            ", vni=" + runAvoidNull(() -> vni, "null") +
            ", packet=" + packet +
            '}';
    }

    public int getFlags() {
        return flags;
    }

    public void setFlags(int flags) {
        clearRawPacket();
        this.flags = flags;
    }

    public int getVni() {
        return vni;
    }

    public void setVni(int vni) {
        clearRawPacket();
        this.vni = vni;
    }

    public AbstractEthernetPacket getPacket() {
        return packet;
    }

    public void setPacket(AbstractEthernetPacket packet) {
        clearRawPacket();
        this.packet = packet;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VXLanPacket that = (VXLanPacket) o;
        return flags == that.flags &&
            vni == that.vni &&
            Objects.equals(packet, that.packet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flags, vni, packet);
    }
}
