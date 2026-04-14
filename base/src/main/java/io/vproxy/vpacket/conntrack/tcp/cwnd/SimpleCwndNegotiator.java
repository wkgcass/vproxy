package io.vproxy.vpacket.conntrack.tcp.cwnd;

public class SimpleCwndNegotiator implements CwndNegotiator {
    private final int[] cwnd;
    private int offset = 0;
    private boolean full = false;
    private int sum = 0;

    public SimpleCwndNegotiator() {
        this(1024);
    }

    public SimpleCwndNegotiator(int historyCount) {
        this.cwnd = new int[historyCount];
    }

    @Override
    public int computeMinCwnd(int cwnd) {
        if (full) {
            sum -= this.cwnd[offset];
        }
        sum += cwnd;
        this.cwnd[offset++] = cwnd;
        if (offset >= this.cwnd.length) {
            offset = 0;
            full = true;
        }
        if (!full) {
            return 0;
        }
        return sum / this.cwnd.length;
    }
}
