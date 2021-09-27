package io.vproxy.base.util.callback;

public class BlockCallback<T, E extends Throwable> extends Callback<T, E> {
    private final Object lock = this; // lock for synchronizing t/ex/done/thread
    private volatile T t;
    private volatile E ex;
    private volatile boolean done = false;
    private volatile Thread waitThread;

    @Override
    protected void onSucceeded(T value) {
        synchronized (lock) {
            t = value;
            done = true;
            if (waitThread != null) {
                waitThread.interrupt();
            }
        }
    }

    @Override
    protected void onFailed(E err) {
        synchronized (lock) {
            ex = err;
            done = true;
            if (waitThread != null) {
                waitThread.interrupt();
            }
        }
    }

    private T doReturn() throws E {
        if (ex != null)
            throw ex;
        return t;
    }

    public T block() throws E {
        if (done)
            return doReturn();
        synchronized (lock) {
            if (done)
                return doReturn();
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
        return doReturn();
    }
}
