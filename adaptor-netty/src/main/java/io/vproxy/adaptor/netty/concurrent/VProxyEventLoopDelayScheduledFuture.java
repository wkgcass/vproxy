package io.vproxy.adaptor.netty.concurrent;

import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.ScheduledFuture;
import io.vproxy.base.Config;
import io.vproxy.base.selector.TimerEvent;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class VProxyEventLoopDelayScheduledFuture<T> extends DefaultPromise<T> implements ScheduledFuture<T> {
    private final long beginTime;
    private final int delay;
    private TimerEvent event;

    public VProxyEventLoopDelayScheduledFuture(int delay) {
        this.beginTime = Config.currentTimestamp;
        this.delay = delay;
    }

    public void setEvent(TimerEvent event) {
        this.event = event;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        long delta = Config.currentTimestamp - beginTime;
        if (delta > delay) {
            return 0;
        } else {
            return delay - (int) delta;
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        event.cancel();
        return super.cancel(mayInterruptIfRunning);
    }

    @Override
    public int compareTo(Delayed o) {
        return (int) (getDelay(TimeUnit.MILLISECONDS) - o.getDelay(TimeUnit.MILLISECONDS));
    }
}
