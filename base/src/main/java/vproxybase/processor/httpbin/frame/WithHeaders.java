package vproxybase.processor.httpbin.frame;

import vproxybase.processor.httpbin.entity.Header;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface WithHeaders {
    boolean endHeaders();

    List<Header> headers();

    default Map<String, String> toMap() {
        var headers = headers();
        if (headers == null) {
            return Map.of();
        }
        Map<String, String> ret = new HashMap<>();
        for (var h : headers) {
            ret.put(h.keyStr, new String(h.value));
        }
        return ret;
    }

    default boolean contains(String key) {
        var headers = headers();
        if (headers == null) {
            return false;
        }
        for (var h : headers) {
            if (h.caseSensitive) {
                if (h.keyStr.equals(key)) return true;
            } else {
                if (h.keyStr.equalsIgnoreCase(key)) return true;
            }
        }
        return false;
    }

    default byte[] get(String key) {
        var headers = headers();
        if (headers == null) {
            return null;
        }
        for (var h : headers) {
            if (h.caseSensitive) {
                if (h.keyStr.equals(key)) return h.value;
            } else {
                if (h.keyStr.equalsIgnoreCase(key)) return h.value;
            }
        }
        return null;
    }
}
