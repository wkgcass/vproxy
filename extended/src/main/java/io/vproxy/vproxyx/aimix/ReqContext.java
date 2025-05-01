package io.vproxy.vproxyx.aimix;

import io.vproxy.lib.http.StorageKey;

public class ReqContext {
    public static final StorageKey<ReqContext> KEY = new StorageKey<>() {
    };

    public boolean reasoningTagResponded = false;
    public final String token;
    public String id;
    public boolean isReasoningContent = false;
    public boolean isLineBeginning = false;
    public boolean isFirstResponse = true;
    public boolean isRoleSent = false;
    public final boolean isStream;

    public ReqContext(boolean isStream, String token) {
        this.isStream = isStream;
        this.token = token;
    }

    public void checkAndSetId(String id) {
        if (this.id == null) {
            this.id = id;
        }
    }
}
