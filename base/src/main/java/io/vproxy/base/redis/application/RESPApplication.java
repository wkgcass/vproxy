package io.vproxy.base.redis.application;

import io.vproxy.base.util.callback.Callback;

import java.util.List;

public interface RESPApplication<CTX extends RESPApplicationContext> {
    CTX context();

    default List<RESPCommand> commands() {
        return null; // default return null, means (no commands)
    }

    void handle(Object o, CTX ctx, Callback<Object, Throwable> cb);
}
