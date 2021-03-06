package vproxy.vpacket;

import vproxy.base.util.ByteArray;
import vproxy.base.util.Consts;
import vproxy.base.util.Logger;
import vproxy.base.util.Utils;
import vproxy.vfd.IP;
import vproxy.vfd.IPv4;

import java.util.Objects;

public class Ipv4Packet extends AbstractIpPacket {
    private int version;
    private int ihl; // Internet Header Length
    private int dscp; // Differentiated Services Code Point
    private int ecn; // Explicit Congestion Notification
    private int totalLength;
    private int identification;
    private int flags;
    private int fragmentOffset;
    private int ttl;
    private int protocol;
    private int headerChecksum;
    private IPv4 src;
    private IPv4 dst;
    private ByteArray options;
    private AbstractPacket packet;

    @Override
    public String readIPProto(ByteArray bytes) {
        if (bytes.length() < 20) {
            return "input packet length too short for an ip packet";
        }
        protocol = bytes.uint8(9);
        initUpperLayerPacket();
        return null;
    }

    @Override
    public String from(ByteArray bytes) {
        if (bytes.length() < 20) {
            return "input packet length too short for an ip packet";
        }

        // 0-3
        int versionAndIHL = bytes.uint8(0);
        version = (versionAndIHL >> 4) & 0xff;
        if (version != 4) {
            return "invalid version for ipv4 packet: " + version;
        }
        ihl = versionAndIHL & 0x0f;
        if (bytes.length() < ihl * 4) {
            return "input packet smaller than ihl(" + ihl + ") specified";
        }
        if (ihl < 5) {
            return "input packet ihl(" + ihl + ") < 5";
        }
        int dscpAndECN = bytes.uint8(1);
        dscp = (dscpAndECN >> 2) & 0xff;
        ecn = dscpAndECN & 0b00000011;
        totalLength = bytes.uint16(2);
        if (totalLength < ihl * 4) {
            return "input ihl(" + ihl + ") > totalLength(" + totalLength + ")";
        }
        if (totalLength < bytes.length()) {
            assert Logger.lowLevelDebug("ipv4 packet is cut shorter from " + bytes.length() + " to " + totalLength);
            bytes = bytes.sub(0, totalLength);
        } else if (totalLength > bytes.length()) {
            return "totalLength(" + totalLength + ") > input.length(" + bytes.length() + ")";
        }

        // 4-7
        identification = bytes.uint16(4);
        int flagsAndFragmentOffsetFirstByte = bytes.get(6);
        flags = (flagsAndFragmentOffsetFirstByte >> 5) & 0xff;
        int fragmentOffsetFirstByte = flagsAndFragmentOffsetFirstByte & 0b00011111;
        int fragmentOffsetSecondByte = bytes.get(7);
        fragmentOffset = (fragmentOffsetFirstByte << 8) | fragmentOffsetSecondByte;

        // 8-11
        ttl = bytes.uint8(8);
        protocol = bytes.uint8(9);
        headerChecksum = bytes.uint16(10);

        // 12-20
        byte[] srcBytes = bytes.sub(12, 4).toJavaArray();
        src = IP.fromIPv4(srcBytes);
        byte[] dstBytes = bytes.sub(16, 4).toJavaArray();
        dst = IP.fromIPv4(dstBytes);

        // options
        if (ihl > 5) {
            options = bytes.sub(20, ihl * 4 - 20);
        } else {
            options = ByteArray.allocate(0);
        }

        // packet
        ByteArray bytesForPacket = bytes.sub(ihl * 4, totalLength - ihl * 4);
        initUpperLayerPacket();
        packet.recordParent(this);
        String err = packet.from(bytesForPacket);
        if (err != null) {
            return err;
        }

        raw = bytes;

        return null;
    }

    private void initUpperLayerPacket() {
        if (protocol == Consts.IP_PROTOCOL_ICMP) {
            packet = new IcmpPacket(false);
        } else if (protocol == Consts.IP_PROTOCOL_TCP) {
            packet = new TcpPacket();
        } else {
            packet = new PacketBytes();
        }
    }

    @Override
    protected ByteArray buildPacket() {
        // prepare
        int headerLen = 20;
        if (options != null) {
            headerLen += options.length();
        }

        // pre-check
        if (headerLen % 4 != 0)
            throw new IllegalArgumentException("header length % 4 != 0: " + headerLen);

        // fix values
        ihl = headerLen / 4;
        ByteArray packetByteArray = packet.getRawPacket();
        totalLength = headerLen + packetByteArray.length();

        // generate
        ByteArray arr = genHeaderWithChecksumUnfilled()
            .concat(options)
            .concat(packetByteArray);

        headerChecksum = calculateChecksum(arr);
        arr.int16(10, headerChecksum);
        return arr;
    }

    @Override
    protected void updateChecksum() {
        raw.int16(10, 0);
        int cksum = calculateChecksum(raw.sub(0, 20));
        headerChecksum = cksum;
        raw.int16(10, cksum);

        if (packet instanceof TcpPacket) {
            if (packet.requireUpdatingChecksum) {
                ((TcpPacket) packet).updateChecksumWithIPv4(this);
            }
        } else {
            packet.checkAndUpdateChecksum();
        }
    }

    @Override
    public String description() {
        return "ip"
            + ",nw_src=" + (src == null ? "not-parsed-yet" : src.formatToIPString())
            + ",nw_dst=" + (dst == null ? "not-parsed-yet" : dst.formatToIPString())
            + "," + (packet == null ? "not-parsed-yet" : packet.description());
    }

    private ByteArray genHeaderWithChecksumUnfilled() {
        byte flagsAndFragmentOffsetFirstByte = (byte) (
            ((flags << 5) & 0xff)
                |
                ((fragmentOffset >> 8) & 0xff)
        );
        return ByteArray.allocate(6)
            .set(0, (byte) ((version << 4) | ihl)).set(1, (byte) ((dscp << 2) | ecn)).int16(2, totalLength)
            .int16(4, identification).concat(
                ByteArray.allocate(6)
                    .set(0, flagsAndFragmentOffsetFirstByte).set(1, (byte) fragmentOffset)
                    .set(2, (byte) ttl).set(3, (byte) protocol) /*skip the checksum here*/)
            .concat(ByteArray.from(src.getAddress()))
            .concat(ByteArray.from(dst.getAddress()));
    }

    public int calculateChecksum() {
        return calculateChecksum(genHeaderWithChecksumUnfilled());
    }

    private int calculateChecksum(ByteArray arr) {
        return Utils.calculateChecksum(arr, 20);
    }

    @Override
    public String toString() {
        return "Ipv4Packet{" +
            "version=" + version +
            ", ihl=" + ihl +
            ", dscp=" + dscp +
            ", ecn=" + ecn +
            ", totalLength=" + totalLength +
            ", identification=" + identification +
            ", flags=" + flags +
            ", fragmentOffset=" + fragmentOffset +
            ", ttl=" + ttl +
            ", protocol=" + protocol +
            ", headerChecksum=" + headerChecksum +
            ", src=" + src +
            ", dst=" + dst +
            ", options=" + options +
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

    public int getIhl() {
        return ihl;
    }

    public void setIhl(int ihl) {
        clearRawPacket();
        this.ihl = ihl;
    }

    public int getDscp() {
        return dscp;
    }

    public void setDscp(int dscp) {
        clearRawPacket();
        this.dscp = dscp;
    }

    public int getEcn() {
        return ecn;
    }

    public void setEcn(int ecn) {
        clearRawPacket();
        this.ecn = ecn;
    }

    public int getTotalLength() {
        return totalLength;
    }

    public void setTotalLength(int totalLength) {
        clearRawPacket();
        this.totalLength = totalLength;
    }

    public int getIdentification() {
        return identification;
    }

    public void setIdentification(int identification) {
        clearRawPacket();
        this.identification = identification;
    }

    public int getFlags() {
        return flags;
    }

    public void setFlags(int flags) {
        clearRawPacket();
        this.flags = flags;
    }

    public int getFragmentOffset() {
        return fragmentOffset;
    }

    public void setFragmentOffset(int fragmentOffset) {
        clearRawPacket();
        this.fragmentOffset = fragmentOffset;
    }

    public int getTtl() {
        return ttl;
    }

    public void setTtl(int ttl) {
        if (raw != null) {
            raw.set(8, (byte) ttl);
            clearChecksum();
        }
        this.ttl = ttl;
    }

    @Override
    public int getProtocol() {
        return protocol;
    }

    public void setProtocol(int protocol) {
        clearRawPacket();
        this.protocol = protocol;
    }

    public int getHeaderChecksum() {
        return headerChecksum;
    }

    public void setHeaderChecksum(int headerChecksum) {
        clearRawPacket();
        this.headerChecksum = headerChecksum;
    }

    @Override
    public IPv4 getSrc() {
        return this.src;
    }

    public void setSrc(IPv4 src) {
        clearRawPacket();
        this.src = src;
    }

    @Override
    public IPv4 getDst() {
        return this.dst;
    }

    public void setDst(IPv4 dst) {
        clearRawPacket();
        this.dst = dst;
    }

    public ByteArray getOptions() {
        return options;
    }

    public void setOptions(ByteArray options) {
        clearRawPacket();
        this.options = options;
    }

    @Override
    public AbstractPacket getPacket() {
        return this.packet;
    }

    @Override
    public int getHopLimit() {
        return getTtl();
    }

    @Override
    public void setHopLimit(int n) {
        setTtl(n);
    }

    @Override
    public void setPacket(AbstractPacket packet) {
        clearRawPacket();
        this.packet = packet;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Ipv4Packet that = (Ipv4Packet) o;
        return version == that.version &&
            ihl == that.ihl &&
            dscp == that.dscp &&
            ecn == that.ecn &&
            totalLength == that.totalLength &&
            identification == that.identification &&
            flags == that.flags &&
            fragmentOffset == that.fragmentOffset &&
            ttl == that.ttl &&
            protocol == that.protocol &&
            headerChecksum == that.headerChecksum &&
            Objects.equals(src, that.src) &&
            Objects.equals(dst, that.dst) &&
            Objects.equals(options, that.options) &&
            Objects.equals(packet, that.packet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, ihl, dscp, ecn, totalLength, identification, flags, fragmentOffset, ttl, protocol, headerChecksum, src, dst, options, packet);
    }
}
