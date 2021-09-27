package io.vproxy.base.util.ratelimit;

import io.vproxy.base.Config;

public class SimpleRateLimiter extends RateLimiter {
    public final long capacity;
    public final long fillRate; // per millisecond
    private long tokens;

    private long lastTs; // millis

    public SimpleRateLimiter(long capacity, long fillRate) {
        this.capacity = capacity;
        this.fillRate = fillRate;
        this.tokens = capacity;
        this.lastTs = Config.currentTimestamp;
    }

    @Override
    public boolean acquire(long n) {
        if (capacity == -1) {
            return true;
        }

        long currentTs = Config.currentTimestamp;
        if (currentTs < lastTs) { // in case concurrency occurred
            currentTs = lastTs;
        }
        long delta = currentTs - lastTs;
        long tokens = this.tokens + delta * fillRate;
        if (tokens > capacity) {
            tokens = capacity;
        }
        if (tokens < n) {
            this.tokens = tokens;
            this.lastTs = currentTs;
            return false;
        }
        tokens -= n;
        this.tokens = tokens;
        this.lastTs = currentTs;
        return true;
    }
}
