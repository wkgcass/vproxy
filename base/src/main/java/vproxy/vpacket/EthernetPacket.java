package vproxy.vpacket;

import vproxy.base.util.*;
import vproxy.vfd.MacAddress;

import java.util.Objects;

public class EthernetPacket extends AbstractEthernetPacket {
    private MacAddress dst;
    private MacAddress src;
    private int type;
    private AbstractPacket packet;

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
        boolean mayIgnoreError = false;
        if (type == Consts.ETHER_TYPE_ARP) {
            packet = new ArpPacket();
        } else if (type == Consts.ETHER_TYPE_IPv4) {
            mayIgnoreError = true;
            packet = new Ipv4Packet();
        } else if (type == Consts.ETHER_TYPE_IPv6) {
            mayIgnoreError = true;
            packet = new Ipv6Packet();
        } else {
            packet = new PacketBytes();
        }
        String err = packet.from(data);
        if (err != null) {
            if (mayIgnoreError) {
                Logger.warn(LogType.SYS_ERROR, "got l3 packet unable to parse, type=" + type + ", packet=" + data.toHexString());
                packet = new PacketBytes();
                packet.from(data);
            } else {
                return err;
            }
        }
        this.packet = packet;

        raw = bytes;
        return null;
    }

    @Override
    protected ByteArray buildPacket() {
        return dst.bytes // dst
            .concat(src.bytes) // src
            .concat(ByteArray.allocate(2).int16(0, type)) // type
            .concat(packet.getRawPacket()); // packet
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

    public void setSrc(MacAddress src) {
        clearRawPacket();
        this.src = src;
    }

    @Override
    public MacAddress getDst() {
        return dst;
    }

    public void setDst(MacAddress dst) {
        clearRawPacket();
        this.dst = dst;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        clearRawPacket();
        this.type = type;
    }

    @Override
    public AbstractPacket getPacket() {
        return packet;
    }

    public void setPacket(AbstractPacket packet) {
        clearRawPacket();
        this.packet = packet;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EthernetPacket that = (EthernetPacket) o;
        return type == that.type &&
            Objects.equals(dst, that.dst) &&
            Objects.equals(src, that.src) &&
            Objects.equals(packet, that.packet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dst, src, type, packet);
    }
}
