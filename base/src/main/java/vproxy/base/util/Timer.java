package vproxy.base.util;

import vproxy.base.Config;
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

    protected long currentTimeMillis() {
        return Config.currentTimestamp;
    }

    public void resetTimer() {
        if (timeout == -1) {
            throw new IllegalArgumentException("timeout is not valid");
        }
        lastStart = currentTimeMillis();
        if (timer == null) {
            timer = loop.delay(timeout, this::checkAndCancel);
        }
    }

    private void checkAndCancel() {
        long current = currentTimeMillis();
        if (current - lastStart > timeout) {
            cancel();
            return;
        }
        long timeLeft = timeout - (current - lastStart);
        timer = loop.delay((int) timeLeft, this::checkAndCancel);
    }

    public void cancel() {
        lastStart = -1;
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    public void setTimeout(int timeout) {
        if (this.timeout == timeout) {
            return; // not changed
        }
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
        timer = loop.delay((int) nextDelay, this::checkAndCancel);
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
