package vproxy.util;

import vproxy.selector.SelectorEventLoop;

public class RunOnLoopCallback<T, E extends Exception> extends Callback<T, E> {
    private final SelectorEventLoop callerLoop;
    private final Callback<T, E> cb;
    private final Thread callerThread;

    public RunOnLoopCallback(Callback<T, E> cb) {
        callerLoop = SelectorEventLoop.current();
        callerThread = Thread.currentThread();
        this.cb = cb;
        if (callerLoop == null) {
            Logger.warn(LogType.ALERT, "RunOnLoopCallback can not retrieve the event loop, current thread is " + callerThread);
        }
    }

    @Override
    protected void onSucceeded(T value) {
        if (callerLoop == null) {
            Logger.warn(LogType.ALERT, "RunOnLoopCallback did not retrieve the event loop, the caller thread is " + callerThread + ", current thread is " + Thread.currentThread());
            cb.succeeded(value);
        } else {
            callerLoop.runOnLoop(() -> cb.succeeded(value));
        }
    }

    @Override
    protected void onFailed(E err) {
        if (callerLoop == null) {
            Logger.warn(LogType.ALERT, "RunOnLoopCallback did not retrieve the event loop, the caller thread is " + callerThread + ", current thread is " + Thread.currentThread());
            cb.failed(err);
        } else {
            callerLoop.runOnLoop(() -> cb.failed(err));
        }
    }
}
