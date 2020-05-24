package vproxy.redis;

import vproxybase.util.Callback;

public interface RESPHandler<T> {
    T attachment();

    void handle(Object input, T attach, Callback<Object, Throwable> cb);
}
