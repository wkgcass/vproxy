package io.vproxy.vpacket;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;

public class LinuxCookedPacket extends AbstractPacket {
    private int type;
    private int addrType;
    private int addrLen;
    private ByteArray addr;
    private int proto;
    private AbstractPacket payload;

    public LinuxCookedPacket() {
    }

    public static final int TYPE_RCV = 0; // the packet was specifically sent to us by somebody else
    public static final int TYPE_BROADCAST_RCV = 1; // the packet was broadcast by somebody else
    public static final int TYPE_MULTICAST_RCV = 2; // the packet was multicast, but not broadcast, by somebody else
    public static final int TYPE_FORWARD = 3; // the packet was sent to somebody else by somebody else
    public static final int TYPE_SND = 4; // the packet was sent by us

    public int getType() {
        return type;
    }

    public void setType(int type) {
        if (raw != null) {
            raw.pktBuf.int16(0, type);
        }
        this.type = type;
    }

    public int getAddrType() {
        return addrType;
    }

    public void setAddrType(int addrType) {
        if (raw != null) {
            raw.pktBuf.int16(2, addrType);
        }
        this.addrType = addrType;
    }

    public int getAddrLen() {
        return addrLen;
    }

    public void setAddrLen(int addrLen) {
        if (raw != null) {
            raw.pktBuf.int16(4, addrLen);
        }
        this.addrLen = addrLen;
    }

    public ByteArray getAddr() {
        return addr;
    }

    public void setAddr(ByteArray addr) {
        if (addr.length() > 8)
            throw new IllegalArgumentException();
        if (addr.length() < 8) {
            addr = addr.concat(ByteArray.allocateInitZero(8 - addr.length()));
        }
        if (raw != null) {
            for (int i = 0; i < 8; ++i) {
                raw.pktBuf.set(6 + i, addr.get(i));
            }
        }
        this.addr = addr;
    }

    public int getProto() {
        return proto;
    }

    public void setProto(int proto) {
        if (raw != null) {
            raw.pktBuf.int16(14, proto);
        }
        this.proto = proto;
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
        if (raw.pktBuf.length() < 16) {
            return "packet too short";
        }

        type = raw.pktBuf.uint16(0);
        addrType = raw.pktBuf.uint16(2);
        addrLen = raw.pktBuf.uint16(4);
        addr = raw.pktBuf.sub(6, 8);
        proto = raw.pktBuf.uint16(14);

        AbstractPacket packet;
        if (proto == Consts.ETHER_TYPE_ARP) {
            packet = new ArpPacket();
        } else if (proto == Consts.ETHER_TYPE_IPv4) {
            packet = new Ipv4Packet();
        } else if (proto == Consts.ETHER_TYPE_IPv6) {
            packet = new Ipv6Packet();
        } else {
            packet = new PacketBytes();
        }
        var data = raw.sub(16);
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
        var p = new LinuxCookedPacket();
        p.setType(type);
        p.setAddrType(addrType);
        p.setAddrLen(addrLen);
        p.setAddr(addr.copy());
        p.setProto(proto);
        p.setPayload(payload.copy());
        return p;
    }

    @Override
    protected ByteArray buildPacket(int flags) {
        var b = ByteArray.allocate(16)
            .int16(0, type)
            .int16(2, addrType)
            .int16(4, addrLen)
            .int16(14, proto);
        for (int i = 0; i < 8; ++i) {
            b.set(6 + i, addr.get(i));
        }
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
        return "type=" + typeDesc() +
               ",l_addr=" + addr.toHexString() +
               "," + payload.description();
    }

    private String typeDesc() {
        switch (type) {
            case TYPE_RCV:
                return "rcv";
            case TYPE_BROADCAST_RCV:
                return "broadcast_rcv";
            case TYPE_MULTICAST_RCV:
                return "multicast_rcv";
            case TYPE_FORWARD:
                return "forward";
            case TYPE_SND:
                return "send";
            default:
                return "" + type;
        }
    }
}
