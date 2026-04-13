package io.vproxy.vpacket.conntrack.tcp;

import io.vproxy.base.util.ByteArray;

public class Segment {
    public final long seqBeginInclusive;
    public final long seqEndExclusive;
    public final ByteArray data;

    public int retransmitted = 0; // 是否经过重传（用于Karn算法，跳过RTT采样）
    public boolean sacked = false; // 是否已被对端SACK确认（后续重传可跳过）

    public Segment(long seqBeginInclusive, ByteArray data) {
        this.seqBeginInclusive = seqBeginInclusive;
        this.seqEndExclusive = seqBeginInclusive + data.length();
        this.data = data;
    }

    @Override
    public String toString() {
        return "Segment[" + seqBeginInclusive + ", " + seqEndExclusive + "){" +
               "retransmitted=" + retransmitted +
               ", sacked=" + sacked +
               '}';
    }
}
