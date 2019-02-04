package net.cassite.vproxy.discovery;

import net.cassite.vproxy.redis.application.RESPApplication;
import net.cassite.vproxy.redis.application.RESPApplicationContext;
import net.cassite.vproxy.util.Callback;

public interface NodeDataHandler extends RESPApplication<RESPApplicationContext> {
    boolean canHandle(String type);

    default RESPApplicationContext context() {
        return null; // will not fire
    }

    void handle(Object o, RESPApplicationContext ctx, Callback<Object, Throwable> cb);
}
