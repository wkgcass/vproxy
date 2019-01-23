package net.cassite.vproxy.redis.entity;

public class RESPError extends RESP {
    public final StringBuilder error = new StringBuilder();

    @Override
    public String toString() {
        return "RESP.Error(" + error + ")";
    }

    @Override
    public Object getJavaObject() {
        return error.toString();
    }
}
