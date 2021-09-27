package io.vproxy.vpacket;

import io.vproxy.base.util.*;
import io.vproxy.vfd.MacAddress;

import java.util.Objects;

public class EthernetPacket extends AbstractPacket {
    public static final int NO_VLAN_CODE = -1;
    public static final int PENDING_VLAN_CODE = -2;

    private MacAddress dst;
    private MacAddress src;
    private int vlan = NO_VLAN_CODE;
    private int type;
    private AbstractPacket packet;

    private PacketDataBuffer packetBytes;

    @Override
    public String from(PacketDataBuffer raw) {
        return from(raw, false);
    }

    public String from(PacketDataBuffer raw, boolean allowPartial) {
        ByteArray bytes = raw.pktBuf;
        String err = from(bytes, null);
        if (err != null) {
            return err;
        }
        PacketDataBuffer data;
        if (vlan < 0) {
            data = raw.sub(14);
        } else {
            // with vlan tag
            data = raw.sub(18);
        }
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
        if (allowPartial && packet instanceof PartialPacket) {
            err = ((PartialPacket) packet).initPartial(data);
            if (err == null) {
                this.packetBytes = data;
            }
        } else {
            err = packet.from(data);
        }
        if (err != null) {
            if (isIPPacket) {
                Logger.warn(LogType.SYS_ERROR, "got l3 packet unable to parse, type=" + type + ", packet=" + data.pktBuf.toHexString() + ": " + err);
                packet = new PacketBytes();
                packet.from(data);
            } else {
                return err;
            }
        }
        this.packet = packet;

        this.raw = raw;
        return null;
    }

    public String from(ByteArray bytes, AbstractPacket packet) {
        if (bytes.length() < (6 /*dst*/ + 6 /*src*/ + 2 /*type*/)) {
            return "input packet length too short for a ethernet packet";
        }
        dst = new MacAddress(bytes.sub(0, 6));
        src = new MacAddress(bytes.sub(6, 6));
        type = bytes.uint16(12);
        if (type == Consts.ETHER_TYPE_8021Q) {
            // handle 802.1q tag
            if (bytes.length() < (6 /*dst*/ + 6 /*src*/ + 2 /*8100*/ + 4 /*tag*/)) {
                return "input packet length too short for 802.1q ethernet packet";
            }
            byte vlan1 = bytes.get(14);
            byte vlan2 = bytes.get(15);
            vlan = ((vlan1 & 0xf) << 8) | (vlan2 & 0xff);
            type = bytes.uint16(16);
        }
        if (packet != null) {
            this.packet = packet;
            packet.recordParent(this);
        }
        return null;
    }

    @Override
    protected ByteArray buildPacket(int flags) {
        ByteArray addrs = dst.bytes.copy() // dst
            .concat(src.bytes.copy()); // src
        if (vlan < 0) {
            return addrs
                .concat(ByteArray.allocate(2).int16(0, type)) // type
                .concat(packet.getRawPacket(flags)); // packet
        }
        // consider 802.1q
        var tagAndType = ByteArray.allocate(6);
        tagAndType.int16(0, Consts.ETHER_TYPE_8021Q);
        tagAndType.int16(2, vlan); // ignore PCP and DEI, only fill in the vid
        tagAndType.int16(4, type); // type
        return addrs.concat(tagAndType).concat(packet.getRawPacket(flags));
    }

    @Override
    protected void __updateChecksum() {
        __updateChildrenChecksum();
    }

    @Override
    protected void __updateChildrenChecksum() {
        packet.updateChecksum();
    }

    public PacketDataBuffer getPacketBytes() {
        return packetBytes;
    }

    @Override
    public void clearAllRawPackets() {
        clearRawPacket();
        getPacket().clearAllRawPackets();
    }

    public void clearPacketBytes() {
        this.packetBytes = null;
    }

    @Override
    public EthernetPacket copy() {
        var ret = new EthernetPacket();
        ret.dst = dst;
        ret.src = src;
        ret.vlan = vlan;
        ret.type = type;
        ret.packet = packet.copy();
        ret.packet.recordParent(ret);
        return ret;
    }

    @Override
    public String description() {
        return "ether"
            + ",dl_dst=" + dst
            + ",dl_src=" + src
            + (vlan >= 0 ? ",vlan=" + vlan : "")
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

    public MacAddress getSrc() {
        return src;
    }

    public void setSrc(MacAddress src) {
        if (raw != null) {
            for (int i = 0; i < src.bytes.length(); ++i) {
                raw.pktBuf.set(6 + i, src.bytes.get(i));
            }
        }
        this.src = src;
    }

    public MacAddress getDst() {
        return dst;
    }

    public void setDst(MacAddress dst) {
        if (raw != null) {
            for (int i = 0; i < dst.bytes.length(); ++i) {
                raw.pktBuf.set(i, dst.bytes.get(i));
            }
        }
        this.dst = dst;
    }

    public int getVlan() {
        return vlan;
    }

    public void setVlan(int vlan) {
        if (raw != null) {
            if (vlan < 0) {
                if ((this.vlan >= 0 || this.vlan == PENDING_VLAN_CODE) && vlan != PENDING_VLAN_CODE) {
                    returnHeadroomAndMove(4, 12);
                    raw.pktBuf.int16(12, type);
                }
                this.vlan = vlan;
            } else {
                if (this.vlan < 0 && this.vlan != PENDING_VLAN_CODE) {
                    if (consumeHeadroomAndMove(4, 12)) {
                        raw.pktBuf.int16(12, Consts.ETHER_TYPE_8021Q);
                        raw.pktBuf.int16(14, vlan); // ignore PCP and DEI, only fill in the vid
                        raw.pktBuf.int16(16, type); // type
                    } else { // cannot get enough headroom
                        clearRawPacket();
                    }
                } else {
                    raw.pktBuf.int16(14, vlan);
                }
            }
        }
        this.vlan = vlan;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        clearRawPacket();
        this.type = type;
    }

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
            vlan == that.vlan &&
            Objects.equals(packet, that.packet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dst, src, type, packet);
    }
}
