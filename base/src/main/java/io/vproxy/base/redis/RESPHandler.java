package io.vproxy.base.redis;

import io.vproxy.base.util.callback.Callback;

public interface RESPHandler<T> {
    T attachment();

    void handle(Object input, T attach, Callback<Object, Throwable> cb);
}
