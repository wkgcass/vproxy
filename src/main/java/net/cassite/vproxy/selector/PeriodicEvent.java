package net.cassite.vproxy.selector;

public class PeriodicEvent {
    private final Runnable runnable;
    private final SelectorEventLoop loop;
    private final int delay;
    private boolean running = false;
    private TimerEvent te;

    PeriodicEvent(Runnable runnable, SelectorEventLoop loop, int delay) {
        this.runnable = runnable;
        this.loop = loop;
        this.delay = delay;
    }

    void start() {
        running = true;
        te = loop.delay(delay, this::run);
    }

    private void run() {
        if (running) {
            runnable.run();
            te = loop.delay(delay, this::run);
        }
    }

    public void cancel() {
        running = false;
        if (te != null) {
            te.cancel();
            te = null;
        }
    }
}
