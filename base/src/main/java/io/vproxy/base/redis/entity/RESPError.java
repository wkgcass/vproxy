package vproxy.base.redis.entity;

import vproxy.base.util.exception.XException;

public class RESPError extends RESP {
    public final StringBuilder error = new StringBuilder();

    @Override
    public String toString() {
        return "RESP.Error(" + error + ")";
    }

    @Override
    public Object getJavaObject() {
        return new XException(error.toString());
    }
}
