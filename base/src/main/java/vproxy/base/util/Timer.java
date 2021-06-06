package vproxy.base.util;

import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.selector.TimerEvent;
import vproxy.vfd.FDProvider;

public class Timer {
    public final SelectorEventLoop loop;
    private int timeout;
    private long lastStart = -1;
    private TimerEvent timer;

    public Timer(SelectorEventLoop loop, int timeout) {
        this.loop = loop;
        this.timeout = timeout;
    }

    public void resetTimer() {
        if (timer != null) {
            timer.cancel();
        }
        lastStart = FDProvider.get().currentTimeMillis();
        timer = loop.delay(timeout, this::cancel);
    }

    public void cancel() {
        lastStart = -1;
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
        if (lastStart == -1) { // not started yet
            return;
        }
        long current = FDProvider.get().currentTimeMillis();
        if (current - lastStart > timeout) {
            // should timeout immediately
            // run in next tick to prevent some concurrent modification on sets
            SelectorEventLoop currentLoop = SelectorEventLoop.current();
            if (currentLoop != null) {
                currentLoop.nextTick(this::cancel);
            } else {
                loop.nextTick(this::cancel);
            }
            return;
        }
        if (timer != null) {
            timer.cancel();
        }
        long nextDelay = lastStart + timeout - current;
        lastStart = current;
        timer = loop.delay((int) nextDelay, this::cancel);
    }

    public int getTimeout() {
        return timeout;
    }

    public long getTTL() {
        if (lastStart == -1) {
            return -1;
        }
        return timeout - (FDProvider.get().currentTimeMillis() - lastStart);
    }
}
