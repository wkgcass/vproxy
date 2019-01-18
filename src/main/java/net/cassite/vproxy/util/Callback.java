package net.cassite.vproxy.util;

public abstract class Callback<T, E extends Throwable> {
    private boolean called = false;

    protected abstract void onSucceeded(T value);

    protected abstract void onFailed(E err);

    public final boolean isCalled() {
        return called;
    }

    public final void succeeded(T value) {
        if (called) {
            Logger.error(LogType.IMPROPER_USE, "callback already called");
            return;
        }
        called = true;
        onSucceeded(value);
    }

    public final void failed(E err) {
        if (called) {
            Logger.error(LogType.IMPROPER_USE, "callback already called");
            return;
        }
        called = true;
        onFailed(err);
    }
}
