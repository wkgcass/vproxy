package net.cassite.vproxy.redis;

import net.cassite.vproxy.util.Callback;

public interface RESPHandler<T> {
    T attachment();

    void handle(Object input, T attach, Callback<Object, Throwable> cb);
}
