package io.vproxy.base.redis.entity;

import io.vproxy.base.util.io.ArrayOutputStream;

public class RESPBulkString extends RESP {
    public int negative = 1;
    public int len;
    public ArrayOutputStream data; // may be null

    @Override
    public String toString() {
        return "RESP.BulkString(" + data + ")";
    }

    @Override
    public Object getJavaObject() {
        return data == null ? null : data.toString();
    }
}
