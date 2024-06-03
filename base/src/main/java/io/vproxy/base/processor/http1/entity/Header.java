package io.vproxy.base.processor.http1.entity;

import io.vproxy.base.http.IHttpHeader;

public class Header implements IHttpHeader {
    public String key; // notnull
    public String value; // notnull

    public Header() {
    }

    public Header(String key, String value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public String toString() {
        return "Header{" +
               "key='" + key + '\'' +
               ", value='" + value + '\'' +
               '}';
    }

    @Override
    public String keyAsString() {
        return key;
    }

    @Override
    public StringBuilder keyAsStringBuilder() {
        return new StringBuilder(key);
    }

    @Override
    public byte[] keyAsBytes() {
        return key.getBytes();
    }

    @Override
    public String valueAsString() {
        return value;
    }

    @Override
    public StringBuilder valueAsStringBuilder() {
        return new StringBuilder(value);
    }

    @Override
    public byte[] valueAsBytes() {
        return value.getBytes();
    }

    @Override
    public boolean keyEqualsIgnoreCase(String key) {
        return this.key.equalsIgnoreCase(key);
    }
}
