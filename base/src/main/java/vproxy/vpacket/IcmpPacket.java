package vproxy.vpacket;

import vproxy.base.util.ByteArray;
import vproxy.base.util.Consts;
import vproxy.base.util.Utils;

import java.util.Objects;

public class IcmpPacket extends AbstractPacket implements PartialPacket {
    private int type;
    private int code;
    private int checksum;
    private ByteArray other;

    private final boolean isIpv6;

    public IcmpPacket(boolean isIpv6) {
        this.isIpv6 = isIpv6;
    }

    @Override
    public String initPartial(PacketDataBuffer raw) {
        this.raw = raw;
        return null;
    }

    @Override
    public String from(PacketDataBuffer raw) {
        ByteArray bytes = raw.pktBuf;
        if (bytes.length() < 8) {
            return "input packet length too short for a icmp packet";
        }
        type = bytes.uint8(0);
        code = bytes.uint8(1);
        checksum = bytes.uint16(2);
        other = bytes.sub(4, bytes.length() - 4);

        this.raw = raw;
        return null;
    }

    @Override
    protected ByteArray buildPacket() {
        if (isIpv6)
            throw new UnsupportedOperationException("this packet is ICMPv6");

        ByteArray ret = ByteArray.allocate(4).set(0, (byte) type).set(1, (byte) code)/*skip checksum here*/.concat(other);
        checksum = Utils.calculateChecksum(ret, ret.length());
        ret.int16(2, checksum);
        return ret;
    }

    @Override
    protected void __updateChecksum() {
        if (isIpv6)
            throw new UnsupportedOperationException("this packet is ICMPv6");

        // TODO not used for now
    }

    @Override
    public IcmpPacket copy() {
        var ret = new IcmpPacket(isIpv6);
        ret.type = type;
        ret.code = code;
        ret.checksum = checksum;
        ret.other = other;
        return ret;
    }

    @Override
    public String description() {
        return isIpv6 ? "icmp6" : "icmp";
    }

    public ByteArray getRawICMPv6Packet(Ipv6Packet ipv6) {
        if (!isIpv6)
            throw new UnsupportedOperationException("this packet is ICMP, not v6");
        if (raw == null) {
            raw = new PacketDataBuffer(buildICMPv6Packet(ipv6));
        }
        return raw.pktBuf;
    }

    private ByteArray buildICMPv6Packet(Ipv6Packet ipv6) {
        if (!isIpv6)
            throw new UnsupportedOperationException("this packet is ICMP, not v6");

        ByteArray ret = ByteArray.allocate(4).set(0, (byte) type).set(1, (byte) code)/*skip checksum here*/.concat(other);

        ByteArray pseudoHeader = Utils.buildPseudoIPv6Header(ipv6, Consts.IP_PROTOCOL_ICMPv6, ret.length());

        ByteArray toCalculate = pseudoHeader.concat(ret);
        checksum = Utils.calculateChecksum(toCalculate, toCalculate.length());
        ret.int16(2, checksum);
        return ret;
    }

    protected void updateChecksumWithIPv6(Ipv6Packet ipv6) {
        if (!isIpv6)
            throw new UnsupportedOperationException("this packet is ICMP, not v6");

        raw.pktBuf.int16(2, 0);
        ByteArray pseudoHeader = Utils.buildPseudoIPv6Header(ipv6, Consts.IP_PROTOCOL_ICMPv6, raw.pktBuf.length());
        ByteArray toCalculate = pseudoHeader.concat(raw.pktBuf);
        checksum = Utils.calculateChecksum(toCalculate, toCalculate.length());
        raw.pktBuf.int16(2, checksum);

        requireUpdatingChecksum = false;
    }

    @Override
    public String toString() {
        return "IcmpPacket{" +
            "type=" + type +
            ", code=" + code +
            ", checksum=" + checksum +
            ", other=" + other +
            '}';
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        clearRawPacket();
        this.type = type;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        clearRawPacket();
        this.code = code;
    }

    public int getChecksum() {
        return checksum;
    }

    public void setChecksum(int checksum) {
        clearRawPacket();
        this.checksum = checksum;
    }

    public ByteArray getOther() {
        return other;
    }

    public void setOther(ByteArray other) {
        clearRawPacket();
        this.other = other;
    }

    public boolean isIpv6() {
        return isIpv6;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IcmpPacket that = (IcmpPacket) o;
        return type == that.type &&
            code == that.code &&
            checksum == that.checksum &&
            Objects.equals(other, that.other) &&
            isIpv6 == that.isIpv6;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, code, checksum, other, isIpv6);
    }
}
