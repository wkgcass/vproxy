package io.vproxy.base.util.promise;

import io.vproxy.base.util.callback.BlockCallback;
import io.vproxy.base.util.callback.Callback;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.callback.BlockCallback;
import io.vproxy.base.util.callback.Callback;
import vproxy.base.util.coll.Tuple;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Promise<T> {
    public Promise(BiConsumer<
        Consumer<T>, // resolutionFunc
        Consumer<Throwable>> // rejectionFunc
                       f) {
        f.accept(this::resolution, this::rejection);
    }

    private volatile boolean done = false;
    private volatile T value;
    private volatile Throwable err;
    private volatile BiConsumer<T, Throwable> handler;

    private void resolution(T value) {
        synchronized (this) {
            if (done) {
                Logger.error(LogType.IMPROPER_USE, "promise is already done but is calling resolution with " + value, new Throwable("stacktrace"));
                return;
            }
            this.done = true;
            this.value = value;
            if (this.handler == null) {
                return;
            }
        }
        this.handler.accept(value, null);
    }

    private void rejection(Throwable err) {
        Objects.requireNonNull(err);
        synchronized (this) {
            if (done) {
                Logger.error(LogType.IMPROPER_USE, "promise is already done but is calling rejection with " + err, new Throwable("stacktrace"));
                return;
            }
            this.done = true;
            this.err = err;
            if (this.handler == null) {
                return;
            }
        }
        this.handler.accept(null, err);
    }

    public void setHandler(BiConsumer<T, Throwable> handler) {
        synchronized (this) {
            if (this.handler != null) {
                throw new IllegalStateException("handler is already set");
            }
            this.handler = handler;
            if (!this.done) {
                return;
            }
        }
        handler.accept(value, err);
    }

    public <U> Promise<U> then(ThrowableFunction<T, Promise<U>> thenFunc) {
        Objects.requireNonNull(thenFunc);
        var tup = Promise.<U>todo();
        var cb = tup.right;
        setHandler((v, e) -> {
            if (e != null) {
                cb.failed(e);
            } else {
                Promise<U> p;
                try {
                    p = thenFunc.apply(v);
                } catch (Throwable ex) {
                    cb.failed(ex);
                    return;
                }
                p.setHandler((u, f) -> {
                    if (f != null) {
                        cb.failed(f);
                    } else {
                        cb.succeeded(u);
                    }
                });
            }
        });
        return tup.left;
    }

    public Promise<T> exception(ThrowableFunction<Throwable, Promise<T>> catchFunc) {
        Objects.requireNonNull(catchFunc);
        var tup = Promise.<T>todo();
        var cb = tup.right;
        setHandler((v, e) -> {
            if (e != null) {
                Promise<T> p;
                try {
                    p = catchFunc.apply(e);
                } catch (Throwable ex) {
                    cb.failed(ex);
                    return;
                }
                p.setHandler((t, f) -> {
                    if (f != null) {
                        cb.failed(f);
                    } else {
                        cb.succeeded(t);
                    }
                });
            } else {
                cb.succeeded(v);
            }
        });
        return tup.left;
    }

    public T block() throws Throwable {
        BlockCallback<T, Throwable> blockCB = new BlockCallback<>();
        setHandler((t, e) -> {
            if (e != null) {
                blockCB.failed(e);
            } else {
                blockCB.succeeded(t);
            }
        });
        return blockCB.block();
    }

    public static <T> Tuple<Promise<T>, Callback<T, Throwable>> todo() {
        //noinspection unchecked
        Callback<T, Throwable>[] callbackPtr = new Callback[1];
        Promise<T> promise = new Promise<>((resolutionFunc, rejectionFunc) -> callbackPtr[0] = new Callback<>() {
            @Override
            protected void onSucceeded(T value) {
                resolutionFunc.accept(value);
            }

            @Override
            protected void onFailed(Throwable err) {
                rejectionFunc.accept(err);
            }
        });
        return new Tuple<>(promise, callbackPtr[0]);
    }

    public static <T, E extends Throwable> Promise<T> wrap(Consumer<Callback<T, E>> f) {
        return new Promise<>((resolution, rejection) -> {
            Callback<T, E> cb = new Callback<T, E>() {
                @Override
                protected void onSucceeded(T value) {
                    resolution.accept(value);
                }

                @Override
                protected void onFailed(E err) {
                    rejection.accept(err);
                }
            };
            f.accept(cb);
        });
    }

    public static <T> Promise<T> resolve(T t) {
        return new Promise<>((resolution, rejection) -> resolution.accept(t));
    }

    public static <T> Promise<T> reject(Throwable t) {
        return new Promise<>((resolution, rejection) -> rejection.accept(t));
    }
}
