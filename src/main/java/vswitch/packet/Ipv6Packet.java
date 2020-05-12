package vswitch.packet;

import vfd.IP;
import vfd.IPv6;
import vproxy.util.ByteArray;
import vswitch.util.Consts;

import java.util.ArrayList;
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
    public String from(ByteArray bytes) {
        if (bytes.length() < 40) {
            return "input packet length too short for an ipv6 packet";
        }
        byte b0 = bytes.get(0);
        byte b1 = bytes.get(1);

        // 0-3
        version = (b0 >> 4) & 0x0f;
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
        if (40 + payloadLength != bytes.length()) {
            return "input packet length does not correspond to payloadLength(" + payloadLength + ")";
        }

        byte[] srcBytes = bytes.sub(8, 16).toJavaArray();
        byte[] dstBytes = bytes.sub(24, 16).toJavaArray();
        src = IP.fromIPv6(srcBytes);
        dst = IP.fromIPv6(dstBytes);

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

        ByteArray bytesForPacket = bytes.sub(40 + skipLengthForExtHeaders, bytes.length() - 40 - skipLengthForExtHeaders);
        int protocol = nextHeader;
        if (!extHeaders.isEmpty()) {
            protocol = extHeaders.get(extHeaders.size() - 1).nextHeader;
        }
        if (protocol == Consts.IPv6_NEXT_HEADER_NO_NEXT_HEADER) {
            if (bytesForPacket.length() != 0) {
                return "invalid packet: getting next header " + protocol + "(NO_NEXT_HEADER) but the input bytes length for next packet is not 0";
            }
        }
        if (protocol == Consts.IP_PROTOCOL_ICMP || protocol == Consts.IP_PROTOCOL_ICMPv6) {
            packet = new IcmpPacket(protocol == Consts.IP_PROTOCOL_ICMPv6);
        } else {
            packet = new PacketBytes();
        }
        String err = packet.from(bytesForPacket);
        if (err != null) {
            return err;
        }

        raw = bytes;

        return null;
    }

    @Override
    protected ByteArray buildPacket() {
        byte b0 = (byte) ((version << 4) | ((trafficClass >> 4) & 0x0f));
        byte b1 = (byte) ((trafficClass << 4) | ((flowLabel >> 16) & 0x0f));
        ByteArray headers = ByteArray.allocate(8).set(0, b0).set(1, b1).int16(2, flowLabel)
            .int16(4, payloadLength).set(6, (byte) nextHeader).set(7, (byte) hopLimit)
            .concat(ByteArray.from(src.getAddress())).concat(ByteArray.from(dst.getAddress()));
        for (var h : extHeaders) {
            headers = headers.concat(h.getRawPacket());
        }
        if (packet instanceof IcmpPacket && ((IcmpPacket) packet).isIpv6()) {
            return headers.concat(((IcmpPacket) packet).getRawICMPv6Packet(this));
        } else {
            return headers.concat(packet.getRawPacket());
        }
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
        clearRawPacket();
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
        clearRawPacket();
        this.src = src;
    }

    @Override
    public IPv6 getDst() {
        return dst;
    }

    public void setDst(IPv6 dst) {
        clearRawPacket();
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

    public static class ExtHeader extends AbstractPacket {
        private int nextHeader;
        private int hdrExtLen;
        private ByteArray other;

        @Override
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

            raw = bytes.sub(0, 8 + hdrExtLen);
            return null;
        }

        @Override
        protected ByteArray buildPacket() {
            return ByteArray.allocate(2).set(0, (byte) nextHeader).set(1, (byte) hdrExtLen).concat(other);
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
