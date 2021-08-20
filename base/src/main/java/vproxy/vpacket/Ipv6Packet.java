package vproxy.vpacket;

import vproxy.base.util.ByteArray;
import vproxy.base.util.Consts;
import vproxy.base.util.Logger;
import vproxy.vfd.IP;
import vproxy.vfd.IPv6;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Ipv6Packet extends AbstractIpPacket {
    private int version;
    private int trafficClass;
    private int flowLabel;
    private int payloadLength;
    private int nextHeader;
    private int hopLimit;
    private IPv6 src;
    private IPv6 dst;
    private List<ExtHeader> extHeaders;
    private AbstractPacket packet;

    @Override
    public String initPartial(PacketDataBuffer raw) {
        ByteArray bytes = raw.pktBuf;
        if (bytes.length() < 40) {
            return "input packet length too short for an ipv6 packet";
        }

        nextHeader = bytes.uint8(6);
        if (Consts.IPv6_needs_next_header.contains(nextHeader)) {
            return from(raw); // must run a full load to ensure the upper level packet is parsed
        }
        extHeaders = Collections.emptyList();

        payloadLength = bytes.uint16(4);
        if (payloadLength == 0) {
            return "we do not support Jumbo Payload for now";
        }
        if (40 + payloadLength > bytes.length()) {
            return "40+payloadLength(" + payloadLength + ") > input.length(" + bytes.length() + ")";
        }

        ByteArray srcBytes = bytes.sub(8, 16);
        ByteArray dstBytes = bytes.sub(24, 16);
        src = IP.fromIPv6(srcBytes.toJavaArray());
        dst = IP.fromIPv6(dstBytes.toJavaArray());
        String err = initUpperLayerPacket(nextHeader, raw.sub(40, payloadLength));
        if (err != null) {
            return err;
        }
        hopLimit = bytes.uint8(7);

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
        return from(raw, true);
    }

    public String from(PacketDataBuffer raw, boolean mustParse) {
        ByteArray bytes = raw.pktBuf;
        if (raw == this.raw /* the same byte array */ && version != 0 /* already parsed */ && !mustParse) {
            return null;
        }

        if (bytes.length() < 40) {
            return "input packet length too short for an ipv6 packet";
        }
        byte b0 = bytes.get(0);
        byte b1 = bytes.get(1);

        // 0-3
        var version = (b0 >> 4) & 0x0f;
        trafficClass = ((b0 << 4) & 0xf0) | ((b1 >> 4) & 0x0f);
        flowLabel = ((b1 & 0x0f) << 16) | bytes.uint16(2);

        if (version != 6) {
            return "invalid version for ipv6 packet: " + version;
        }

        // 4-7
        payloadLength = bytes.uint16(4);
        nextHeader = bytes.uint8(6);
        hopLimit = bytes.uint8(7);

        if (payloadLength == 0) {
            return "we do not support Jumbo Payload for now";
        }
        if (40 + payloadLength < bytes.length()) {
            assert Logger.lowLevelDebug("ipv6 packet is cut shorter from " + bytes.length() + " to " + (40 + payloadLength));
            setPktBufLen(raw, 40 + payloadLength);
            bytes = raw.pktBuf;
        } else if (40 + payloadLength > bytes.length()) {
            return "40+payloadLength(" + payloadLength + ") > input.length(" + bytes.length() + ")";
        }

        ByteArray srcBytes = bytes.sub(8, 16);
        ByteArray dstBytes = bytes.sub(24, 16);
        src = IP.fromIPv6(srcBytes.toJavaArray());
        dst = IP.fromIPv6(dstBytes.toJavaArray());

        int skipLengthForExtHeaders = 0;
        extHeaders = new ArrayList<>();
        if (Consts.IPv6_needs_next_header.contains(nextHeader)) {
            int xh = nextHeader;
            ByteArray xhBuf = bytes.sub(40, bytes.length() - 40);
            while (Consts.IPv6_needs_next_header.contains(xh)) {
                ExtHeader h = new ExtHeader();
                String err = h.from(xhBuf);
                if (err != null) {
                    return err;
                }
                h.recordParent(this);
                extHeaders.add(h);

                xh = h.nextHeader;
                int len = h.getRawPacket().length();
                if (xhBuf.length() < len) {
                    return "invalid packet length too short for next header";
                }
                skipLengthForExtHeaders += len;
                xhBuf = xhBuf.sub(0, len);
            }
        }

        PacketDataBuffer bytesForPacket = raw.sub(40 + skipLengthForExtHeaders, bytes.length() - 40 - skipLengthForExtHeaders);
        int protocol = nextHeader;
        if (!extHeaders.isEmpty()) {
            protocol = extHeaders.get(extHeaders.size() - 1).nextHeader;
        }
        if (protocol == Consts.IPv6_NEXT_HEADER_NO_NEXT_HEADER) {
            if (bytesForPacket.pktBuf.length() != 0) {
                return "invalid packet: getting next header " + protocol + "(NO_NEXT_HEADER) but the input bytes length for next packet is not 0";
            }
        }
        initUpperLayerPacket(protocol, null);
        String err = packet.from(bytesForPacket);
        if (err != null) {
            return err;
        }

        this.version = version; // the version field is used to indicate parsing done, so assign it last
        this.raw = raw;

        return null;
    }

    private String initUpperLayerPacket(int protocol, PacketDataBuffer raw) {
        if (packet != null) {
            return null;
        }
        if (protocol == Consts.IP_PROTOCOL_ICMP || protocol == Consts.IP_PROTOCOL_ICMPv6) {
            packet = new IcmpPacket(protocol == Consts.IP_PROTOCOL_ICMPv6);
        } else if (protocol == Consts.IP_PROTOCOL_TCP) {
            packet = new TcpPacket();
        } else if (protocol == Consts.IP_PROTOCOL_UDP) {
            packet = new UdpPacket();
        } else {
            packet = new PacketBytes();
        }
        if (raw != null && packet instanceof PartialPacket) {
            String err = ((PartialPacket) packet).initPartial(raw);
            if (err != null) {
                return err;
            }
        }
        packet.recordParent(this);
        return null;
    }

    @Override
    protected ByteArray buildPacket() {
        byte b0 = (byte) ((version << 4) | ((trafficClass >> 4) & 0x0f));
        byte b1 = (byte) ((trafficClass << 4) | ((flowLabel >> 16) & 0x0f));
        ByteArray headers = ByteArray.allocate(8).set(0, b0).set(1, b1).int16(2, flowLabel)
            .int16(4, payloadLength).set(6, (byte) nextHeader).set(7, (byte) hopLimit)
            .concat(src.bytes.copy()).concat(dst.bytes.copy());
        for (var h : extHeaders) {
            headers = headers.concat(h.getRawPacket());
        }
        if (packet.raw == null) {
            if (packet instanceof IcmpPacket && ((IcmpPacket) packet).isIpv6()) {
                ((IcmpPacket) packet).getRawICMPv6Packet(this);
            } else if (packet instanceof TcpPacket) {
                ((TcpPacket) packet).buildIPv6TcpPacket(this);
            } else if (packet instanceof UdpPacket) {
                ((UdpPacket) packet).buildIPv6UdpPacket(this);
            }
        } else {
            if (packet instanceof IcmpPacket && ((IcmpPacket) packet).isIpv6()) {
                ((IcmpPacket) packet).updateChecksumWithIPv6(this);
            } else if (packet instanceof TcpPacket) {
                ((TcpPacket) packet).updateChecksumWithIPv6(this);
            } else if (packet instanceof UdpPacket) {
                ((UdpPacket) packet).updateChecksumWithIPv6(this);
            }
        }
        return headers.concat(packet.getRawPacket());
    }

    @Override
    protected void __updateChecksum() {
        if (packet instanceof IcmpPacket && ((IcmpPacket) packet).isIpv6()) {
            if (packet.requireUpdatingChecksum) {
                ((IcmpPacket) packet).updateChecksumWithIPv6(this);
            }
        } else if (packet instanceof TcpPacket) {
            if (packet.requireUpdatingChecksum) {
                ((TcpPacket) packet).updateChecksumWithIPv6(this);
            }
        } else if (packet instanceof UdpPacket) {
            if (packet.requireUpdatingChecksum) {
                ((UdpPacket) packet).updateChecksumWithIPv6(this);
            }
        } else {
            packet.checkAndUpdateChecksum();
        }
    }

    @Override
    public void clearChecksum() {
        super.clearChecksum();
        if (packet instanceof IcmpPacket || packet instanceof TcpPacket || packet instanceof UdpPacket) {
            packet.clearChecksum();
        }
    }

    @Override
    public Ipv6Packet copy() {
        var ret = new Ipv6Packet();
        ret.version = version;
        ret.trafficClass = trafficClass;
        ret.flowLabel = flowLabel;
        ret.payloadLength = payloadLength;
        ret.nextHeader = nextHeader;
        ret.hopLimit = hopLimit;
        ret.src = src;
        ret.dst = dst;
        ret.extHeaders = new ArrayList<>(extHeaders.size());
        for (var h : extHeaders) {
            var x = h.copy();
            ret.extHeaders.add(x);
            x.recordParent(ret);
        }
        ret.packet = packet.copy();
        ret.packet.recordParent(ret);
        return ret;
    }

    @Override
    public void clearAllRawPackets() {
        super.clearAllRawPackets();
        for (var h : extHeaders) {
            h.clearAllRawPackets();
        }
    }

    @Override
    public String description() {
        return "ipv6"
            + ",ipv6_src=" + (src == null ? "not-parsed-yet" : src.formatToIPString())
            + ",ipv6_dst=" + (dst == null ? "not-parsed-yet" : dst.formatToIPString())
            + "," + (packet == null ? "not-parsed-yet" : packet.description());
    }

    @Override
    public String toString() {
        return "Ipv6Packet{" +
            "version=" + version +
            ", trafficClass=" + trafficClass +
            ", flowLabel=" + flowLabel +
            ", payloadLength=" + payloadLength +
            ", nextHeader=" + nextHeader +
            ", hopLimit=" + hopLimit +
            ", src=" + src +
            ", dst=" + dst +
            ", extHeaders=" + extHeaders +
            ", packet=" + packet +
            '}';
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        clearRawPacket();
        this.version = version;
    }

    public int getTrafficClass() {
        return trafficClass;
    }

    public void setTrafficClass(int trafficClass) {
        clearRawPacket();
        this.trafficClass = trafficClass;
    }

    public int getFlowLabel() {
        return flowLabel;
    }

    public void setFlowLabel(int flowLabel) {
        clearRawPacket();
        this.flowLabel = flowLabel;
    }

    public int getPayloadLength() {
        return payloadLength;
    }

    public void setPayloadLength(int payloadLength) {
        clearRawPacket();
        this.payloadLength = payloadLength;
    }

    public int getNextHeader() {
        return nextHeader;
    }

    public void setNextHeader(int nextHeader) {
        clearRawPacket();
        this.nextHeader = nextHeader;
    }

    @Override
    public int getHopLimit() {
        return hopLimit;
    }

    @Override
    public void setHopLimit(int hopLimit) {
        if (raw != null) {
            raw.pktBuf.set(7, (byte) hopLimit);
        }
        this.hopLimit = hopLimit;
    }

    @Override
    public int getProtocol() {
        if (extHeaders.isEmpty()) return nextHeader;
        return extHeaders.get(extHeaders.size() - 1).nextHeader;
    }

    @Override
    public IPv6 getSrc() {
        return src;
    }

    public void setSrc(IPv6 src) {
        if (raw != null) {
            for (int i = 0; i < 16; ++i) {
                raw.pktBuf.set(8 + i, src.getAddress()[i]);
            }
            clearChecksum();
        }
        this.src = src;
    }

    @Override
    public IPv6 getDst() {
        return dst;
    }

    public void setDst(IPv6 dst) {
        if (raw != null) {
            for (int i = 0; i < 16; ++i) {
                raw.pktBuf.set(24 + i, dst.getAddress()[i]);
            }
            clearChecksum();
        }
        this.dst = dst;
    }

    public List<ExtHeader> getExtHeaders() {
        return extHeaders;
    }

    public void setExtHeaders(List<ExtHeader> extHeaders) {
        clearRawPacket();
        this.extHeaders = extHeaders;
    }

    @Override
    public AbstractPacket getPacket() {
        return packet;
    }

    @Override
    public void setPacket(AbstractPacket packet) {
        clearRawPacket();
        this.packet = packet;
    }

    @Override
    public void setPacket(int protocol, AbstractPacket packet) {
        setPacket(packet);
        if (extHeaders.isEmpty()) nextHeader = protocol;
        else extHeaders.get(extHeaders.size() - 1).nextHeader = protocol;
    }

    public static class ExtHeader extends AbstractPacket {
        private int nextHeader;
        private int hdrExtLen;
        private ByteArray other;

        @Override
        public String from(PacketDataBuffer raw) {
            throw new UnsupportedOperationException("use from(ByteArray) instead");
        }

        public String from(ByteArray bytes) {
            if (bytes.length() < 8) {
                return "input packet length too short for an ipv6 ext hdr packet";
            }
            nextHeader = bytes.uint8(0);
            hdrExtLen = bytes.uint8(1);
            if (bytes.length() < 8 + hdrExtLen) {
                return "input packet length too short for an ipv6 ext hdr packet";
            }
            other = bytes.sub(2, 6 + hdrExtLen);

            raw = new PacketDataBuffer(bytes.sub(0, 8 + hdrExtLen));
            return null;
        }

        @Override
        protected ByteArray buildPacket() {
            return ByteArray.allocate(2).set(0, (byte) nextHeader).set(1, (byte) hdrExtLen).concat(other);
        }

        @Override
        protected void __updateChecksum() {
            // do nothing
        }

        @Override
        public ExtHeader copy() {
            var ret = new ExtHeader();
            ret.nextHeader = nextHeader;
            ret.hdrExtLen = hdrExtLen;
            ret.other = other;
            return ret;
        }

        @Override
        public String description() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return "ExtHeader{" +
                "nextHeader=" + nextHeader +
                ", hdrExtLen=" + hdrExtLen +
                ", other=" + other +
                '}';
        }

        public int getNextHeader() {
            return nextHeader;
        }

        public void setNextHeader(int nextHeader) {
            clearRawPacket();
            this.nextHeader = nextHeader;
        }

        public int getHdrExtLen() {
            return hdrExtLen;
        }

        public void setHdrExtLen(int hdrExtLen) {
            clearRawPacket();
            this.hdrExtLen = hdrExtLen;
        }

        public ByteArray getOther() {
            return other;
        }

        public void setOther(ByteArray other) {
            clearRawPacket();
            this.other = other;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ExtHeader extHeader = (ExtHeader) o;
            return nextHeader == extHeader.nextHeader &&
                hdrExtLen == extHeader.hdrExtLen &&
                Objects.equals(other, extHeader.other);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nextHeader, hdrExtLen, other);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Ipv6Packet that = (Ipv6Packet) o;
        return version == that.version &&
            trafficClass == that.trafficClass &&
            flowLabel == that.flowLabel &&
            payloadLength == that.payloadLength &&
            nextHeader == that.nextHeader &&
            hopLimit == that.hopLimit &&
            Objects.equals(src, that.src) &&
            Objects.equals(dst, that.dst) &&
            Objects.equals(extHeaders, that.extHeaders) &&
            Objects.equals(packet, that.packet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, trafficClass, flowLabel, payloadLength, nextHeader, hopLimit, src, dst, extHeaders, packet);
    }
}
