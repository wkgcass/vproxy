package vproxybase.redis.application;

import vproxybase.util.Callback;

import java.util.List;

public interface RESPApplication<CTX extends RESPApplicationContext> {
    CTX context();

    default List<RESPCommand> commands() {
        return null; // default return null, means (no commands)
    }

    void handle(Object o, CTX ctx, Callback<Object, Throwable> cb);
}
