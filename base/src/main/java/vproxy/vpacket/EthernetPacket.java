package vproxy.vpacket;

import vproxy.base.util.*;
import vproxy.vfd.MacAddress;

import java.util.Objects;

public class EthernetPacket extends AbstractEthernetPacket {
    private MacAddress dst;
    private MacAddress src;
    private int type;
    private AbstractPacket packet;
    private ByteArray packetBytes;

    @Override
    public String from(ByteArray bytes) {
        return from(bytes, false);
    }

    @Override
    public String from(ByteArray bytes, boolean skipIPPacket) {
        String err = from(bytes, null);
        if (err != null) {
            return err;
        }
        ByteArray data = bytes.sub(14, bytes.length() - 14);
        AbstractPacket packet;
        boolean isIPPacket = false;
        if (type == Consts.ETHER_TYPE_ARP) {
            packet = new ArpPacket();
        } else if (type == Consts.ETHER_TYPE_IPv4) {
            isIPPacket = true;
            packet = new Ipv4Packet();
        } else if (type == Consts.ETHER_TYPE_IPv6) {
            isIPPacket = true;
            packet = new Ipv6Packet();
        } else {
            packet = new PacketBytes();
        }
        packet.recordParent(this);
        if (skipIPPacket && isIPPacket) {
            err = ((AbstractIpPacket) packet).readIPProto(data);
            this.packetBytes = data;
        } else {
            err = packet.from(data);
        }
        if (err != null) {
            if (isIPPacket) {
                Logger.warn(LogType.SYS_ERROR, "got l3 packet unable to parse, type=" + type + ", packet=" + data.toHexString() + ": " + err);
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

    public String from(ByteArray bytes, AbstractPacket packet) {
        if (bytes.length() < (6 /*dst*/ + 6 /*src*/ + 2 /*type*/)) {
            return "input packet length too short for a ethernet packet";
        }
        dst = new MacAddress(bytes.sub(0, 6));
        src = new MacAddress(bytes.sub(6, 6));
        type = bytes.uint16(12);
        if (packet != null) {
            this.packet = packet;
            packet.recordParent(this);
        }
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
    protected void updateChecksum() {
        packet.checkAndUpdateChecksum();
    }

    @Override
    public String description() {
        return "ether"
            + ",dl_dst=" + dst
            + ",dl_src=" + src
            + "," + packet.description();
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
        if (raw != null) {
            for (int i = 0; i < src.bytes.length(); ++i) {
                raw.set(6 + i, src.bytes.get(i));
            }
        }
        this.src = src;
    }

    @Override
    public MacAddress getDst() {
        return dst;
    }

    public void setDst(MacAddress dst) {
        if (raw != null) {
            for (int i = 0; i < dst.bytes.length(); ++i) {
                raw.set(i, dst.bytes.get(i));
            }
        }
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
    public ByteArray getPacketBytes() {
        return packetBytes;
    }

    @Override
    public void clearPacketBytes() {
        this.packetBytes = null;
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
