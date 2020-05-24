package vproxybase.util;

public class JoinCallback<T, E extends Throwable> extends Callback<T, E> {
    private final Callback<T, E> cb;

    private final Object lock = this; // lock for synchronizing t/ex/done/thread
    private volatile boolean done = false;
    private volatile Thread waitThread;

    public JoinCallback(Callback<T, E> cb) {
        this.cb = cb;
    }

    @Override
    protected void onSucceeded(T value) {
        cb.succeeded(value);
        done = true;
        if (waitThread != null) {
            waitThread.interrupt();
        }
    }

    @Override
    protected void onFailed(E err) {
        cb.failed(err);
        done = true;
        if (waitThread != null) {
            waitThread.interrupt();
        }
    }

    public void join() {
        if (done)
            return;
        synchronized (lock) {
            if (done)
                return;
            waitThread = Thread.currentThread();
        }
        // wait for it to finish
        while (!done) {
            try {
                Thread.sleep(1048576 /*wait for a long time, we don't care how long*/);
            } catch (InterruptedException ignore) {
                // will be interrupted when done
                // so ignore error
            }
        }
    }
}
