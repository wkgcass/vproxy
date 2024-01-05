package io.vproxy.vpacket;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Utils;
import io.vproxy.vfd.FDProvider;

public class PcapPacket {
    private int tssec;
    private int tsusec;
    private int capLen; /* number of octets of packet saved in file */
    private int origLen;
    private AbstractPacket packet;

    public PcapPacket() {
    }

    public PcapPacket(AbstractPacket packet) {
        this(FDProvider.get().currentTimeMillis(), packet);
    }

    public PcapPacket(long ts, AbstractPacket packet) {
        this.tssec = (int) (ts / 1000);
        this.tsusec = (int) (ts % 1000) * 1000;
        this.packet = packet;
    }

    public PcapPacket(int tssec, int tsusec, int capLen, int origLen) {
        this.tssec = tssec;
        this.tsusec = tsusec;
        this.capLen = capLen;
        this.origLen = origLen;
    }

    public int getTssec() {
        return tssec;
    }

    public void setTssec(int tssec) {
        this.tssec = tssec;
    }

    public int getTsusec() {
        return tsusec;
    }

    public void setTsusec(int tsusec) {
        this.tsusec = tsusec;
    }

    public int getCapLen() {
        return capLen;
    }

    public void setCapLen(int capLen) {
        this.capLen = capLen;
    }

    public int getOrigLen() {
        return origLen;
    }

    public void setOrigLen(int origLen) {
        this.origLen = origLen;
    }

    public AbstractPacket getPacket() {
        return packet;
    }

    public void setPacket(AbstractPacket packet) {
        this.packet = packet;
    }

    public ByteArray build() {
        var pkt = packet.getRawPacket(0);
        var origLen = this.origLen;
        if (origLen == 0) {
            origLen = pkt.length();
        }
        return ByteArray.allocate(16)
            .int32ReverseNetworkByteOrder(0, tssec)
            .int32ReverseNetworkByteOrder(4, tsusec)
            .int32ReverseNetworkByteOrder(8, pkt.length())
            .int32ReverseNetworkByteOrder(12, origLen)
            .concat(pkt);
    }

    @Override
    public String toString() {
        return "ts=" + Utils.formatTimestampForLogging(tssec * 1000L + tsusec / 1000) +
               "," + packet.description();
    }
}
