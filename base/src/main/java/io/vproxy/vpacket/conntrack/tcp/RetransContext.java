package io.vproxy.vpacket.conntrack.tcp;

public class RetransContext {
    public long lastBeginSeq;
    public int retransmissionCount;
    public boolean sackRetransmit;

    public RetransContext() {
    }

    public RetransContext sackRetransmit() {
        sackRetransmit = true;
        return this;
    }

    public RetransContext lastBeginSeq(long lastBeginSeq) {
        this.lastBeginSeq = lastBeginSeq;
        return this;
    }

    public RetransContext retransmissionCount(int retransmissionCount) {
        this.retransmissionCount = retransmissionCount;
        return this;
    }

    public boolean isSpecialAck() {
        return sackRetransmit;
    }

    public RetransContext resetSpecialAck() {
        sackRetransmit = false;
        return this;
    }
}
