package io.vproxy.vpacket.conntrack.tcp.cwnd;

public class SimpleCwndNegotiator implements CwndNegotiator {
    private final int maxSize;
    private final int[] values;
    private int size;
    private long sum;

    public SimpleCwndNegotiator() {
        this(1024);
    }

    public SimpleCwndNegotiator(int historyCount) {
        this.maxSize = historyCount;
        this.values = new int[historyCount];
    }

    @Override
    public int computeExpectedCwnd(int cwnd) {
        // Binary search: find first index where values[i] > cwnd
        int lo = 0, hi = size;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (values[mid] <= cwnd) lo = mid + 1;
            else hi = mid;
        }
        // Remove all values greater than cwnd (they are at the tail since array is sorted)
        for (int i = lo; i < size; i++) {
            sum -= values[i];
        }
        size = lo;

        // Append cwnd at the end (always valid: remaining values are all <= cwnd)
        if (size >= maxSize) {
            // full, discard the smallest
            sum -= values[0];
            System.arraycopy(values, 1, values, 0, size - 1);
            size--;
        }
        values[size++] = cwnd;
        sum += cwnd;

        return (int) (sum / size);
    }
}
