package io.vproxy.base.redis.entity;

import io.vproxy.base.util.bytearray.ByteArrayBuilder;

public class RESPString extends RESP {
    public final ByteArrayBuilder buffer = new ByteArrayBuilder();

    @Override
    public String toString() {
        return "RESP.String(" + buffer + ")";
    }

    @Override
    public Object getJavaObject() {
        return buffer;
    }
}
