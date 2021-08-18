package vproxy.vpacket;

import vproxy.base.util.ByteArray;
import vproxy.base.util.Consts;
import vproxy.base.util.Utils;

import java.util.Objects;

public class UdpPacket extends TransportPacket {
    private int srcPort;
    private int dstPort;
    private int length;
    private int checksum;
    private AbstractPacket data;

    @Override
    public String initPartial(PacketDataBuffer raw) {
        ByteArray bytes = raw.pktBuf;
        if (bytes.length() < 8) {
            return "input packet length too short for an udp packet";
        }
        srcPort = bytes.uint16(0);
        dstPort = bytes.uint16(2);

        this.raw = raw;
        return null;
    }

    @Override
    public String from(PacketDataBuffer raw) {
        ByteArray bytes = raw.pktBuf;

        if (bytes.length() < 8) {
            return "input packet length too short for an udp packet";
        }
        srcPort = bytes.uint16(0);
        dstPort = bytes.uint16(2);
        length = bytes.uint16(4);
        checksum = bytes.uint16(6);
        if (bytes.length() != length) {
            return "udp packet length not matching the input bytes length";
        }
        PacketBytes pktBytes = new PacketBytes();
        pktBytes.from(new PacketDataBuffer(bytes.sub(8, bytes.length() - 8)));
        data = pktBytes;

        this.raw = raw;

        return null;
    }

    @Override
    protected ByteArray buildPacket() {
        throw new UnsupportedOperationException();
    }

    private ByteArray buildCommonPart() {
        ByteArray ret = ByteArray.allocate(8);
        ret.int16(0, srcPort);
        ret.int16(2, dstPort);
        ret.int16(4, length);
        ret.int16(6, 0); // leave the checksum empty
        ret = ret.concat(data.getRawPacket());
        return ret;
    }

    public ByteArray buildIPv4UdpPacket(Ipv4Packet ipv4) {
        var common = buildCommonPart();

        var pseudo = Utils.buildPseudoIPv4Header(ipv4, Consts.IP_PROTOCOL_UDP, common.length());
        var toCalculate = pseudo.concat(common);
        checksum = Utils.calculateChecksum(toCalculate, toCalculate.length());

        if (checksum == 0) {
            checksum = 0xffff;
        }
        // write checksum
        common.int16(6, checksum);

        // done
        this.raw = new PacketDataBuffer(common);
        return common;
    }

    public ByteArray buildIPv6UdpPacket(Ipv6Packet ipv6) {
        var common = buildCommonPart();

        var pseudo = Utils.buildPseudoIPv6Header(ipv6, Consts.IP_PROTOCOL_UDP, common.length());
        var toCalculate = pseudo.concat(common);
        checksum = Utils.calculateChecksum(toCalculate, toCalculate.length());

        if (checksum == 0) {
            checksum = 0xffff;
        }
        // write checksum
        common.int16(6, checksum);

        // done
        this.raw = new PacketDataBuffer(common);
        return common;
    }

    @Override
    protected void __updateChecksum() {
        throw new UnsupportedOperationException();
    }

    protected void updateChecksumWithIPv4(Ipv4Packet ipv4) {
        raw.pktBuf.int16(6, 0);
        var pseudo = Utils.buildPseudoIPv4Header(ipv4, Consts.IP_PROTOCOL_UDP, raw.pktBuf.length());
        var toCalculate = pseudo.concat(raw.pktBuf);
        var cksum = Utils.calculateChecksum(toCalculate, toCalculate.length());

        if (cksum == 0) {
            cksum = 0xffff;
        }
        checksum = cksum;
        raw.pktBuf.int16(6, cksum);

        requireUpdatingChecksum = false;
    }

    protected void updateChecksumWithIPv6(Ipv6Packet ipv6) {
        raw.pktBuf.int16(6, 0);
        var pseudo = Utils.buildPseudoIPv6Header(ipv6, Consts.IP_PROTOCOL_UDP, raw.pktBuf.length());
        var toCalculate = pseudo.concat(raw.pktBuf);
        var cksum = Utils.calculateChecksum(toCalculate, toCalculate.length());

        if (cksum == 0) {
            cksum = 0xffff;
        }
        checksum = cksum;
        raw.pktBuf.int16(6, cksum);

        requireUpdatingChecksum = false;
    }

    @Override
    public UdpPacket copy() {
        var ret = new UdpPacket();
        ret.srcPort = srcPort;
        ret.dstPort = dstPort;
        ret.length = length;
        ret.checksum = checksum;
        ret.data = data.copy();
        ret.data.recordParent(ret);
        return ret;
    }

    @Override
    public String description() {
        return "udp,tp_src=" + srcPort + ",tp_dst=" + dstPort + ",data=" + (data == null ? "" : data.description());
    }

    @Override
    public int getSrcPort() {
        return srcPort;
    }

    @Override
    public void setSrcPort(int srcPort) {
        if (raw != null) {
            raw.pktBuf.int16(0, srcPort);
            clearChecksum();
        }
        this.srcPort = srcPort;
    }

    @Override
    public int getDstPort() {
        return dstPort;
    }

    @Override
    public void setDstPort(int dstPort) {
        if (raw != null) {
            raw.pktBuf.int16(2, dstPort);
            clearChecksum();
        }
        this.dstPort = dstPort;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        clearRawPacket();
        this.length = length;
    }

    public int getChecksum() {
        return checksum;
    }

    public void setChecksum(int checksum) {
        clearRawPacket();
        this.checksum = checksum;
    }

    public AbstractPacket getData() {
        return data;
    }

    public void setData(AbstractPacket data) {
        clearRawPacket();
        this.data = data;
    }

    @Override
    public void clearAllRawPackets() {
        clearRawPacket();
        data.clearAllRawPackets();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UdpPacket udpPacket = (UdpPacket) o;
        return srcPort == udpPacket.srcPort && dstPort == udpPacket.dstPort && length == udpPacket.length && checksum == udpPacket.checksum && Objects.equals(data, udpPacket.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(srcPort, dstPort, length, checksum, data);
    }

    @Override
    public String toString() {
        return "UdpPacket{" +
            "srcPort=" + srcPort +
            ", dstPort=" + dstPort +
            ", length=" + length +
            ", checksum=" + checksum +
            ", data=" + data +
            '}';
    }
}
