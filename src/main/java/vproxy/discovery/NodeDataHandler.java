package vproxy.discovery;

import vproxy.redis.application.RESPApplication;
import vproxy.redis.application.RESPApplicationContext;
import vproxy.util.Callback;

public interface NodeDataHandler extends RESPApplication<RESPApplicationContext> {
    boolean canHandle(String type);

    default RESPApplicationContext context() {
        return null; // will not fire
    }

    void handle(Object o, RESPApplicationContext ctx, Callback<Object, Throwable> cb);
}
