package io.vproxy.vpacket.conntrack.tcp;

public class SAckTuple {
    public final long seqBeginInclusive;
    public final long seqEndExclusive;

    public SAckTuple(long seqBeginInclusive, long seqEndExclusive) {
        this.seqBeginInclusive = seqBeginInclusive;
        this.seqEndExclusive = seqEndExclusive;
    }

    @Override
    public String toString() {
        return "SAckTuple[" + seqBeginInclusive + ", " + seqEndExclusive + ')';
    }
}
