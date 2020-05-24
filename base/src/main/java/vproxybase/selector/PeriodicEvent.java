package vproxybase.selector;

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

    // no need to handle concurrency of this function
    // it's only called once and called on event loop
    void start() {
        running = true;
        te = loop.delay(delay, this::run);
    }

    private void run() {
        if (running) {
            runnable.run();
            // at this time, it might be canceled
            if (running) {
                te = loop.delay(delay, this::run);
            } else {
                te = null; // set to null in case concurrency
            }
        } else {
            te = null; // set to null in case concurrency
        }
    }

    public void cancel() {
        running = false;
        TimerEvent te = this.te;
        this.te = null;
        if (te != null) {
            te.cancel();
        }
    }
}
