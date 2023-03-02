package io.vproxy.base.redis;

import java.util.Arrays;
import java.util.List;

public class Serializer {
    private Serializer() {
    }

    private static final String _nullStr = "$-1\r\n";
    private static final byte[] _null = _nullStr.getBytes();

    public static byte[] fromNull() {
        return _null;
    }

    private static String fromNullToString() {
        return _nullStr;
    }

    public static byte[] fromInteger(int i) {
        return fromIntegerToString(i).getBytes();
    }

    private static String fromIntegerToString(int i) {
        return ":" + i + "\r\n";
    }

    private static byte[] fromLong(long l) {
        return fromLongToString(l).getBytes();
    }

    private static String fromLongToString(long l) {
        return ":" + l + "\r\n";
    }

    public static byte[] fromString(String s) {
        return fromStringToString(s).getBytes();
    }

    private static String fromStringToString(String s) {
        return "$" + s.length() + "\r\n" + s + "\r\n";
    }

    public static byte[] fromErrorString(String e) {
        return fromErrorToString(e).getBytes();
    }

    private static String fromErrorToString(String e) {
        return "-" + e + "\r\n";
    }

    public static byte[] fromArray(List<Object> arr) {
        return fromArrayToString(arr).getBytes();
    }

    private static String fromArrayToString(List<Object> arr) {
        StringBuilder sb = new StringBuilder("*" + arr.size() + "\r\n");
        for (Object o : arr) {
            sb.append(fromObjectToString(o));
        }
        return sb.toString();
    }

    public static byte[] fromArray(Object[] arr) {
        return fromArray(Arrays.asList(arr));
    }

    private static String fromArrayToString(Object[] arr) {
        return fromArrayToString(Arrays.asList(arr));
    }

    private static String fromObjectToString(Object o) {
        if (o == null)
            return fromNullToString();
        if (o instanceof Integer)
            return fromIntegerToString((Integer) o);
        if (o instanceof Long)
            return fromLongToString((Long) o);
        if (o instanceof String)
            return fromStringToString((String) o);
        if (o instanceof Object[])
            return fromArrayToString((Object[]) o);
        if (o instanceof List) {
            //noinspection unchecked
            return fromArrayToString((List) o);
        }
        throw new IllegalArgumentException("unsupported object type " + o.getClass() + "(" + o + ")");
    }

    public static byte[] from(Object o) {
        return fromObjectToString(o).getBytes();
    }
}
