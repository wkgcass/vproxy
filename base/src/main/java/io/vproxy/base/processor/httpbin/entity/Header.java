package io.vproxy.base.processor.httpbin.entity;

import io.vproxy.base.http.IHttpHeader;

public class Header implements IHttpHeader {
    public final String keyStr;
    public final byte[] key;
    public final byte[] value;
    public final boolean caseSensitive;

    public Header(String key, String value) {
        this(key.toLowerCase().getBytes(), value.getBytes(), false);
    }

    public Header(byte[] key, byte[] value, boolean caseSensitive) {
        this.key = key;
        this.value = value;
        if (caseSensitive) {
            this.keyStr = new String(key);
        } else {
            this.keyStr = new String(key).toLowerCase();
        }
        this.caseSensitive = caseSensitive;
    }

    @Override
    public String toString() {
        return "Header{" +
            "key='" + keyStr + '\'' +
            ", value='" + new String(value) + '\'' +
            ", caseSensitive=" + caseSensitive +
            '}';
    }

    @Override
    public String keyAsString() {
        return keyStr;
    }

    @Override
    public StringBuilder keyAsStringBuilder() {
        return new StringBuilder(keyStr);
    }

    @Override
    public byte[] keyAsBytes() {
        return key;
    }

    @Override
    public String valueAsString() {
        return new String(value);
    }

    @Override
    public StringBuilder valueAsStringBuilder() {
        return new StringBuilder(new String(value));
    }

    @Override
    public byte[] valueAsBytes() {
        return value;
    }

    @Override
    public boolean keyEqualsIgnoreCase(String key) {
        return keyStr.equalsIgnoreCase(key);
    }
}
