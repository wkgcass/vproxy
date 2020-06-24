package vproxybase.redis.entity;

public class RESPInline extends RESP {
    public final StringBuilder string = new StringBuilder();

    @Override
    public String toString() {
        return "RESP.Inline(" + string + ")";
    }

    @Override
    public Object getJavaObject() {
        return string.toString();
    }
}
