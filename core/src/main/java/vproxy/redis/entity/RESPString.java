package vproxy.redis.entity;

public class RESPString extends RESP {
    public final StringBuilder string = new StringBuilder();

    @Override
    public String toString() {
        return "RESP.String(" + string + ")";
    }

    @Override
    public Object getJavaObject() {
        return string.toString();
    }
}
