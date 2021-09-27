package vproxy.base.util.ratelimit;

import vproxy.base.Config;
import vproxy.base.util.coll.Tuple3;

import java.util.Arrays;

public class StatisticsRateLimiter extends RateLimiter {
    private final RateLimiter rl;
    public final int recordingDuration; // millis
    public final int samplingRate; // millis

    private final long[] data;
    private int cursor = 0;
    private long lastTs;

    public StatisticsRateLimiter(RateLimiter rl, int recordingDuration, int samplingRate) {
        this.rl = rl;
        this.recordingDuration = recordingDuration;
        this.samplingRate = samplingRate;

        data = new long[recordingDuration / samplingRate];
        lastTs = formatTs(Config.currentTimestamp);
    }

    private long formatTs(long ts) {
        return ts - ts % samplingRate;
    }

    public Tuple3<Long[], Long, Long> getStatistics(long beginTs, long endTs) {
        return getStatistics(beginTs, endTs, 1);
    }

    public Tuple3<Long[], Long, Long> getStatistics(long beginTs, long endTs, int step) {
        if (beginTs > endTs) {
            throw new IllegalArgumentException("beginTs " + beginTs + " > endTs " + endTs);
        }

        endTs = formatTs(endTs);
        beginTs = formatTs(beginTs);
        int beginDeltaIndexes = (int) ((beginTs - lastTs) / samplingRate);
        int endDeltaIndexes = (int) ((endTs - lastTs) / samplingRate);

        Long[] ret = new Long[endDeltaIndexes - beginDeltaIndexes + 1];

        for (int i = beginDeltaIndexes, retIdx = 0; i <= endDeltaIndexes; ++i, ++retIdx) {
            if (i > 0) {
                continue;
            }
            if (i < 0 && -i >= data.length) {
                continue;
            }
            int idx = cursor + i;
            if (idx < 0) {
                idx += data.length;
            }
            ret[retIdx] = data[idx];
        }

        if (step > 1) {
            Long[] foo = new Long[ret.length / step + (ret.length % step == 0 ? 0 : 1)];
            for (int i = 0; i < ret.length; i += step) {
                long value = 0;
                boolean hasValue = false;
                for (int x = 0; x < step; ++x) {
                    if (ret.length > i + x && ret[i + x] != null) {
                        value += ret[i + x];
                        hasValue = true;
                    }
                }
                if (hasValue) {
                    foo[i / step] = value;
                } else {
                    foo[i / step] = null;
                }
            }
            ret = foo;
            endTs = ((endTs - beginTs) / step) * step + beginTs;
        }

        return new Tuple3<>(ret, beginTs, endTs);
    }

    @Override
    public boolean acquire(long n) {
        boolean ok = rl.acquire(n);
        if (!ok) {
            return false;
        }

        long current = Config.currentTimestamp;
        current = formatTs(current);

        int delta = (int) ((current - lastTs) / samplingRate);
        if (delta < 0) { // in case concurrency occurred
            delta = 0;
        }
        int cursor = this.cursor + delta;
        if (cursor >= data.length && cursor < data.length * 2) {
            cursor = cursor - data.length;
            for (int i = this.cursor + 1; i < data.length; ++i) {
                data[i] = 0;
            }
            for (int i = 0; i < cursor; ++i) {
                data[i] = 0;
            }
        } else if (cursor >= data.length * 2) {
            Arrays.fill(data, 0);
            while (cursor >= data.length) {
                cursor -= data.length;
            }
        }
        if (delta != 0) {
            data[cursor] = 0;
        }
        data[cursor] += n;
        this.cursor = cursor;
        lastTs = current;

        return true;
    }
}
