package io.vproxy.vproxyx.aimix;

import io.vproxy.lib.http.StorageKey;

public class ReqContext {
    public static final StorageKey<ReqContext> KEY = new StorageKey<>() {
    };

    public boolean reasoningTagResponded = false;
}
