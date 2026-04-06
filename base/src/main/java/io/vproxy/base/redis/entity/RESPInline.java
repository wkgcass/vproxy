package io.vproxy.base.redis.entity;

import io.vproxy.base.util.bytearray.ByteArrayBuilder;

public class RESPInline extends RESP {
    public final ByteArrayBuilder buffer = new ByteArrayBuilder();

    @Override
    public String toString() {
        return "RESP.Inline(" + buffer + ")";
    }

    @Override
    public Object getJavaObject() {
        return buffer;
    }
}
