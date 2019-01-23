package net.cassite.vproxy.redis.entity;

public class RESPInteger extends RESP {
    public int negative = 1;
    public int integer;

    @Override
    public String toString() {
        return "RESP.Integer(" + integer + ")";
    }

    @Override
    public Object getJavaObject() {
        return integer;
    }
}
