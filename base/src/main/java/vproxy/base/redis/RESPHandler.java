package vproxy.base.redis;

import vproxy.base.util.callback.Callback;

public interface RESPHandler<T> {
    T attachment();

    void handle(Object input, T attach, Callback<Object, Throwable> cb);
}
