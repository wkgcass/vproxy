package io.vproxy.base.util.callback;

import java.util.concurrent.CountDownLatch;

public class BlockCallback<T, E extends Throwable> extends Callback<T, E> {
    private final Object lock = this; // lock for synchronizing t/ex/done/thread
    private volatile T t;
    private volatile E ex;
    private final CountDownLatch done = new CountDownLatch(1);

    @Override
    protected void onSucceeded(T value) {
        synchronized (lock) {
            t = value;
            done.countDown();
        }
    }

    @Override
    protected void onFailed(E err) {
        synchronized (lock) {
            ex = err;
            done.countDown();
        }
    }

    private T doReturn() throws E {
        if (ex != null)
            throw ex;
        return t;
    }

    public T block() throws E {
        if (done.getCount() == 0)
            return doReturn();
        synchronized (lock) {
            if (done.getCount() == 0)
                return doReturn();
        }
        // wait for it to finish
        while (done.getCount() > 0) {
            try {
                done.await();
            } catch (InterruptedException ignore) {
                // will be interrupted when done
                // so ignore error
            }
        }
        return doReturn();
    }
}
