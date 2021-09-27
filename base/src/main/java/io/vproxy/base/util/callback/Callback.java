package vproxy.base.util.callback;

import vproxy.base.util.LogType;
import vproxy.base.util.Logger;

public abstract class
Callback<T, E extends Throwable> {
    private boolean called = false;

    protected abstract void onSucceeded(T value);

    protected abstract void onFailed(E err);

    protected void doFinally() {

    }

    public final boolean isCalled() {
        return called;
    }

    public final void succeeded() {
        succeeded(null);
    }

    public final void succeeded(T value) {
        if (called) {
            Logger.error(LogType.IMPROPER_USE, "callback already called", new Exception("already called when getting result " + value));
            return;
        }
        called = true;
        onSucceeded(value);
        doFinally();
    }

    public final void failed(E err) {
        if (called) {
            Logger.error(LogType.IMPROPER_USE, "callback already called", new Exception("already called when getting an exception", err));
            return;
        }
        called = true;
        onFailed(err);
        doFinally();
    }

    public final void finish(E err) {
        finish(err, null);
    }

    public final void finish(E err, T value) {
        if (err != null) {
            failed(err);
        } else {
            succeeded(value);
        }
    }
}
