package vproxy.util;

import vproxy.selector.SelectorEventLoop;

public class RunOnLoopCallback<T, E extends Exception> extends Callback<T, E> {
    private final SelectorEventLoop callerLoop;
    private final Callback<T, E> cb;

    public RunOnLoopCallback(Callback<T, E> cb) {
        callerLoop = SelectorEventLoop.current();
        this.cb = cb;
    }

    @Override
    protected void onSucceeded(T value) {
        if (callerLoop == null) {
            cb.succeeded(value);
        } else {
            callerLoop.runOnLoop(() -> cb.succeeded(value));
        }
    }

    @Override
    protected void onFailed(E err) {
        if (callerLoop == null) {
            cb.failed(err);
        } else {
            callerLoop.runOnLoop(() -> cb.failed(err));
        }
    }
}
