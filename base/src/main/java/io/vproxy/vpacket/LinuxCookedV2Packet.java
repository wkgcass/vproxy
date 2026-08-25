package io.vproxy.vpacket;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;

public class LinuxCookedV2Packet extends AbstractPacket {
    private int proto;
    private int reserved;
    private long ifIndex;
    private int addrType;
    private int type;
    private int addrLen;
    private ByteArray addr;
    private AbstractPacket payload;

    public static final int TYPE_RCV = LinuxCookedPacket.TYPE_RCV;
    public static final int TYPE_BROADCAST_RCV = LinuxCookedPacket.TYPE_BROADCAST_RCV;
    public static final int TYPE_MULTICAST_RCV = LinuxCookedPacket.TYPE_MULTICAST_RCV;
    public static final int TYPE_FORWARD = LinuxCookedPacket.TYPE_FORWARD;
    public static final int TYPE_SND = LinuxCookedPacket.TYPE_SND;

    public int getProto() {
        return proto;
    }

    public void setProto(int proto) {
        if (raw != null) {
            raw.pktBuf.int16(0, proto);
        }
        this.proto = proto;
    }

    public int getReserved() {
        return reserved;
    }

    public void setReserved(int reserved) {
        if (raw != null) {
            raw.pktBuf.int16(2, reserved);
        }
        this.reserved = reserved;
    }

    public long getIfIndex() {
        return ifIndex;
    }

    public void setIfIndex(long ifIndex) {
        if (raw != null) {
            raw.pktBuf.int32(4, (int) ifIndex);
        }
        this.ifIndex = ifIndex;
    }

    public int getAddrType() {
        return addrType;
    }

    public void setAddrType(int addrType) {
        if (raw != null) {
            raw.pktBuf.int16(8, addrType);
        }
        this.addrType = addrType;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        if (raw != null) {
            raw.pktBuf.set(10, (byte) type);
        }
        this.type = type;
    }

    public int getAddrLen() {
        return addrLen;
    }

    public void setAddrLen(int addrLen) {
        if (raw != null) {
            raw.pktBuf.set(11, (byte) addrLen);
        }
        this.addrLen = addrLen;
    }

    public ByteArray getAddr() {
        return addr;
    }

    public void setAddr(ByteArray addr) {
        if (addr.length() > 8) {
            throw new IllegalArgumentException();
        }
        if (addr.length() < 8) {
            addr = addr.concat(ByteArray.allocateInitZero(8 - addr.length()));
        }
        if (raw != null) {
            for (int i = 0; i < 8; ++i) {
                raw.pktBuf.set(12 + i, addr.get(i));
            }
        }
        this.addr = addr;
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
        if (raw.pktBuf.length() < 20) {
            return "packet too short";
        }

        proto = raw.pktBuf.uint16(0);
        reserved = raw.pktBuf.uint16(2);
        ifIndex = raw.pktBuf.uint32(4);
        addrType = raw.pktBuf.uint16(8);
        type = raw.pktBuf.uint8(10);
        addrLen = raw.pktBuf.uint8(11);
        addr = raw.pktBuf.sub(12, 8);

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
        var err = packet.from(raw.sub(20));
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
        var p = new LinuxCookedV2Packet();
        p.setProto(proto);
        p.setReserved(reserved);
        p.setIfIndex(ifIndex);
        p.setAddrType(addrType);
        p.setType(type);
        p.setAddrLen(addrLen);
        p.setAddr(addr.copy());
        p.setPayload(payload.copy());
        return p;
    }

    @Override
    protected ByteArray buildPacket(int flags) {
        var b = ByteArray.allocate(20)
            .int16(0, proto)
            .int16(2, reserved)
            .int32(4, (int) ifIndex)
            .int16(8, addrType)
            .set(10, (byte) type)
            .set(11, (byte) addrLen);
        for (int i = 0; i < 8; ++i) {
            b.set(12 + i, addr.get(i));
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
               ",if_index=" + ifIndex +
               ",l_addr=" + addr.toHexString() +
               "," + payload.description();
    }

    private String typeDesc() {
        return switch (type) {
            case TYPE_RCV -> "rcv";
            case TYPE_BROADCAST_RCV -> "broadcast_rcv";
            case TYPE_MULTICAST_RCV -> "multicast_rcv";
            case TYPE_FORWARD -> "forward";
            case TYPE_SND -> "send";
            default -> "" + type;
        };
    }
}
