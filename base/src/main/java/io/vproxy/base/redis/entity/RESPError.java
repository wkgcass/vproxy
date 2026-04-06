package io.vproxy.base.redis.entity;

import io.vproxy.base.util.bytearray.ByteArrayBuilder;
import io.vproxy.base.util.exception.XException;

public class RESPError extends RESP {
    public final ByteArrayBuilder buffer = new ByteArrayBuilder();

    @Override
    public String toString() {
        return "RESP.Error(" + buffer + ")";
    }

    @Override
    public Object getJavaObject() {
        return new XException(buffer.toString());
    }
}
