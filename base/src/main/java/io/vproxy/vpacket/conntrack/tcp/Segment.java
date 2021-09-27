package io.vproxy.vpacket.conntrack.tcp;

import io.vproxy.base.util.ByteArray;

public class Segment {
    public final long seqBeginInclusive;
    public final long seqEndExclusive;
    public final ByteArray data;

    public Segment(long seqBeginInclusive, ByteArray data) {
        this.seqBeginInclusive = seqBeginInclusive;
        this.seqEndExclusive = seqBeginInclusive + data.length();
        this.data = data;
    }
}
