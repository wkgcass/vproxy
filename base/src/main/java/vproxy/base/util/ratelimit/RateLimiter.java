package vproxy.base.util.ratelimit;

public abstract class RateLimiter {
    abstract public boolean acquire(long n);
}
