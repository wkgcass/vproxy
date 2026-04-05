package io.vproxy.vpacket.conntrack.tcp;

import io.vproxy.base.util.ByteArray;

public class Segment {
    public final long seqBeginInclusive;
    public final long seqEndExclusive;
    public final ByteArray data;

    // 快速重传计数器（借鉴KCP）
    public int fastack = 0;
    public int xmit = 0; // 重传次数

    public Segment(long seqBeginInclusive, ByteArray data) {
        this.seqBeginInclusive = seqBeginInclusive;
        this.seqEndExclusive = seqBeginInclusive + data.length();
        this.data = data;
    }
}
