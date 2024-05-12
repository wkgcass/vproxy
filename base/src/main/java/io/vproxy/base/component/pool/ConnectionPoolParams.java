package io.vproxy.base.component.pool;

public class ConnectionPoolParams {
    public int capacity = 0;
    public int highWatermark = -1;
    public int lowWatermark = -1;
    public int keepaliveInterval = 15_000;

    public int idleTimeoutInPool = -1;
    public int idleTimeoutOutOfPool = -1;

    public ConnectionPoolParams() {
    }

    public ConnectionPoolParams setCapacity(int capacity) {
        this.capacity = capacity;
        return this;
    }

    public ConnectionPoolParams setHighWatermark(int highWatermark) {
        this.highWatermark = highWatermark;
        return this;
    }

    public ConnectionPoolParams setLowWatermark(int lowWatermark) {
        this.lowWatermark = lowWatermark;
        return this;
    }

    public ConnectionPoolParams setKeepaliveInterval(int keepaliveInterval) {
        this.keepaliveInterval = keepaliveInterval;
        return this;
    }

    public ConnectionPoolParams setIdleTimeoutInPool(int idleTimeoutInPool) {
        this.idleTimeoutInPool = idleTimeoutInPool;
        return this;
    }

    public ConnectionPoolParams setIdleTimeoutOutOfPool(int idleTimeoutOutOfPool) {
        this.idleTimeoutOutOfPool = idleTimeoutOutOfPool;
        return this;
    }
}
