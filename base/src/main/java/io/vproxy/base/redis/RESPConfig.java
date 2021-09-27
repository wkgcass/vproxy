package io.vproxy.base.redis;

public class RESPConfig {
    int maxParseLen = 16384;

    public RESPConfig setMaxParseLen(int maxParseLen) {
        this.maxParseLen = maxParseLen;
        return this;
    }
}
