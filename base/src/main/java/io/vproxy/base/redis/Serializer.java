package io.vproxy.base.redis;

import io.vproxy.base.util.ByteArray;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class Serializer {
    private Serializer() {
    }

    private static final ByteArray _null = ByteArray.from("$-1\r\n");

    public static ByteArray fromNull() {
        return _null;
    }

    public static ByteArray fromInteger(int i) {
        return ByteArray.from(":" + i + "\r\n");
    }

    public static ByteArray fromLong(long l) {
        return ByteArray.from(":" + l + "\r\n");
    }

    public static ByteArray fromString(String s) {
        if (s.contains("\n") || s.contains("\r")) {
            return fromBulkString(ByteArray.from(s.getBytes(StandardCharsets.UTF_8)));
        } else {
            return ByteArray.from("$" + s.length() + "\r\n" + s + "\r\n");
        }
    }

    public static ByteArray fromBulkString(ByteArray bytes) {
        return ByteArray.from("$" + bytes.length() + "\r\n").concat(bytes).concat(ByteArray.from("\r\n"));
    }

    public static ByteArray fromErrorString(String e) {
        return ByteArray.from("-" + e + "\r\n");
    }

    public static ByteArray fromArray(List<Object> arr) {
        ByteArray ret = ByteArray.from("*" + arr.size() + "\r\n");
        for (Object o : arr) {
            ret = ret.concat(fromObject(o));
        }
        return ret;
    }

    public static ByteArray fromArray(Object[] arr) {
        return fromArray(Arrays.asList(arr));
    }

    private static ByteArray fromObject(Object o) {
        if (o == null)
            return fromNull();
        if (o instanceof Integer)
            return fromInteger((Integer) o);
        if (o instanceof Long)
            return fromLong((Long) o);
        if (o instanceof String)
            return fromString((String) o);
        if (o instanceof Object[])
            return fromArray((Object[]) o);
        if (o instanceof List) {
            //noinspection unchecked
            return fromArray((List) o);
        }
        throw new IllegalArgumentException("unsupported object type " + o.getClass() + "(" + o + ")");
    }

    public static ByteArray from(Object o) {
        return fromObject(o);
    }
}
