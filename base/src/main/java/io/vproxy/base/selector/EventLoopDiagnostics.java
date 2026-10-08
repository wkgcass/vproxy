package io.vproxy.base.selector;

import io.vproxy.base.util.Logger;

/** Opt-in wall duration counters. SELECT is waiting, not callback CPU time. */
final class EventLoopDiagnostics {
    static final int TASK = 0, TIMER = 1, READ = 2, WRITE = 3, ACCEPT = 4,
        CONNECT = 5, SELECT = 6, BEFORE_POLL = 7, AFTER_POLL = 8;
    private static final String[] NAMES = {
        "task", "timer", "read", "write", "accept", "connect", "select", "beforePoll", "afterPoll"
    };
    final Metric[] metrics = new Metric[NAMES.length];
    private long lastReport;
    private int reportCount;
    private boolean recorded;

    EventLoopDiagnostics() {
        for (int i = 0; i < metrics.length; ++i) metrics[i] = new Metric();
    }

    void record(int kind, Object owner, long elapsed) {
        recorded = true;
        Metric m = metrics[kind];
        elapsed = Math.max(0, elapsed);
        ++m.count;
        m.nanos += elapsed;
        if (elapsed > m.maxNanos) {
            m.maxNanos = elapsed;
            // Retain only a class name, never the callback, FD or endpoint.
            m.maxOwner = owner == null ? "none" : owner.getClass().getName();
        }
        long upper = 1000;
        int bucket = 0;
        while (bucket < 31 && elapsed > upper) { ++bucket; upper <<= 1; }
        ++m.histogram[bucket];
    }

    void report(long now) {
        if (!recorded || reportCount >= 32 ||
            (reportCount > 0 && now - lastReport < 15_000_000_000L)) return;
        lastReport = now;
        ++reportCount;
        String thread = Thread.currentThread().getName();
        for (int i = 0; i < metrics.length; ++i) {
            Metric m = metrics[i];
            if (m.count == 0) continue;
            Logger.alert("[LOOP-DIAG] thread=" + thread + " kind=" + NAMES[i] +
                " count=" + m.count + " totalNs=" + m.nanos + " maxNs=" + m.maxNanos +
                " p95NsUpperBound=" + m.percentileUpperBound(95) +
                " p99NsUpperBound=" + m.percentileUpperBound(99) + " maxOwner=" + m.maxOwner);
        }
    }

    static final class Metric {
        private long count, nanos, maxNanos;
        private String maxOwner = "none";
        private final long[] histogram = new long[32];

        private long percentileUpperBound(int percentile) {
            if (count == 0) return 0;
            long rank = count / 100 * percentile + (count % 100 * percentile + 99) / 100;
            long seen = 0;
            for (int i = 0; i < histogram.length; ++i) {
                seen += histogram[i];
                if (seen >= rank) return i == 31 ? Long.MAX_VALUE : 1000L << i;
            }
            return Long.MAX_VALUE;
        }
    }
}
