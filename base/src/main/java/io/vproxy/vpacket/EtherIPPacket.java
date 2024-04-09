package io.vproxy.vpacket;

import io.vproxy.base.util.ByteArray;

public class EtherIPPacket extends AbstractPacket implements PartialPacket {
    private int version = 3;
    private EthernetPacket packet;

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        if (raw != null) {
            int n = raw.pktBuf.get(0);
            n = n & 0b00001111;
            n = n | ((version << 4) & 0b11110000);
            raw.pktBuf.set(0, (byte) n);
        }
        this.version = version;
    }

    public EthernetPacket getPacket() {
        return packet;
    }

    public void setPacket(EthernetPacket packet) {
        clearRawPacket();
        this.packet = packet;
    }

    @Override
    public String initPartial(PacketDataBuffer raw) {
        ByteArray bytes = raw.pktBuf;
        if (bytes.length() < 2) {
            return "input packet length too short for an etherip packet";
        }
        version = (raw.pktBuf.get(0) >> 4) & 0b1111;
        var ether = new EthernetPacket();
        var err = ether.from(raw.sub(2), true);
        if (err != null) {
            return err;
        }
        ether.recordParent(this);

        this.raw = raw;
        return null;
    }

    @Override
    public String initPartial(int level) {
        if (packet instanceof PartialPacket) {
            return ((PartialPacket) packet).initPartial(level);
        }
        return null;
    }

    @Override
    public String from(PacketDataBuffer raw) {
        if (raw.pktBuf.length() < 2) {
            return "input packet length too short for an etherip packet";
        }
        version = (raw.pktBuf.get(0) >> 4) & 0b1111;
        var ether = new EthernetPacket();
        var err = ether.from(raw.sub(2));
        if (err != null) {
            return err;
        }
        ether.recordParent(this);
        this.packet = ether;
        return null;
    }

    @Override
    public AbstractPacket copy() {
        var pkt = new EtherIPPacket();
        pkt.setVersion(version);
        pkt.setPacket(packet.copy());
        return pkt;
    }

    @Override
    protected ByteArray buildPacket(int flags) {
        var buf = ByteArray.allocate(2);
        buf.set(0, (byte) (version << 4));
        return buf.concat(packet.buildPacket(flags));
    }

    @Override
    protected void __updateChecksum() {
        __updateChildrenChecksum();
    }

    @Override
    protected void __updateChildrenChecksum() {
        packet.updateChecksum();
    }

    @Override
    public String description() {
        return "etherip," + packet.description();
    }
}
