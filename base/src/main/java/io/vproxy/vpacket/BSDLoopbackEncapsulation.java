package io.vproxy.vpacket;

import io.vproxy.base.util.ByteArray;

public class BSDLoopbackEncapsulation extends AbstractPacket {
    private int type;
    private AbstractPacket payload;

    public BSDLoopbackEncapsulation() {
    }

    public static final int TYPE_IPv4 = 2;
    public static final int TYPE_IPv6 = 24;
    public static final int TYPE_IPv6_2 = 28;
    public static final int TYPE_IPv6_3 = 30;

    public int getType() {
        return type;
    }

    public void setType(int type) {
        if (raw != null) {
            raw.pktBuf.int32ReverseNetworkByteOrder(0, type);
        }
        this.type = type;
    }

    public AbstractPacket getPayload() {
        return payload;
    }

    public void setPayload(AbstractPacket payload) {
        clearRawPacket();
        this.payload = payload;
    }

    @Override
    public void clearAllRawPackets() {
        clearRawPacket();
        getPayload().clearAllRawPackets();
    }

    @Override
    public String from(PacketDataBuffer raw) {
        if (raw.pktBuf.length() < 4) {
            return "packet too short";
        }

        type = raw.pktBuf.int32ReverseNetworkByteOrder(0);

        AbstractPacket packet;
        if (type == TYPE_IPv4) {
            packet = new Ipv4Packet();
        } else if (type == TYPE_IPv6 || type == TYPE_IPv6_2 || type == TYPE_IPv6_3) {
            packet = new Ipv6Packet();
        } else {
            packet = new PacketBytes();
        }
        var data = raw.sub(4);
        var err = packet.from(data);
        if (err != null) {
            return err;
        }

        packet.recordParent(this);
        setPayload(packet);

        this.raw = raw;
        return null;
    }

    @Override
    public AbstractPacket copy() {
        var p = new BSDLoopbackEncapsulation();
        p.setType(type);
        p.setPayload(payload.copy());
        return p;
    }

    @Override
    protected ByteArray buildPacket(int flags) {
        var b = ByteArray.allocate(4)
            .int32ReverseNetworkByteOrder(0, type);
        return b.concat(payload.getRawPacket(flags));
    }

    @Override
    protected void __updateChecksum() {
        payload.__updateChecksum();
    }

    @Override
    protected void __updateChildrenChecksum() {
        payload.__updateChecksum();
    }

    @Override
    public String description() {
        return "bsd," + payload.description();
    }
}
