package net.cassite.vproxy.redis.application;

import net.cassite.vproxy.util.Callback;

import java.util.List;

public interface RESPApplication<CTX extends RESPApplicationContext> {
    CTX context();

    List<RESPCommand> commands();

    void handle(Object o, CTX ctx, Callback<Object, Throwable> cb);
}
