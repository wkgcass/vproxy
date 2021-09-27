package io.vproxy.base.redis.entity;

public class RESPBulkString extends RESP {
    public int negative = 1;
    public int len;
    public StringBuilder string; // may be null

    @Override
    public String toString() {
        return "RESP.BulkString(" + string + ")";
    }

    @Override
    public Object getJavaObject() {
        return string == null ? null : string.toString();
    }
}
