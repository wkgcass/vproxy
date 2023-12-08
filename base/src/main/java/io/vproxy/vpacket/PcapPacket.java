package io.vproxy.vpacket;

import io.vproxy.base.util.Utils;

public class PcapPacket {
    private int tssec;
    private int tsusec;
    private int inclLen; /* number of octets of packet saved in file */
    private int origLen;
    private AbstractPacket packet;

    public PcapPacket() {
    }

    public PcapPacket(int tssec, int tsusec, int inclLen, int origLen) {
        this.tssec = tssec;
        this.tsusec = tsusec;
        this.inclLen = inclLen;
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

    public int getInclLen() {
        return inclLen;
    }

    public void setInclLen(int inclLen) {
        this.inclLen = inclLen;
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

    @Override
    public String toString() {
        return "ts=" + Utils.formatTimestampForLogging(tssec * 1000L + tsusec / 1000) +
               "," + packet.description();
    }
}
