package io.vproxy.base.processor.http1.builder;

import io.vproxy.base.http.IHttpHeader;
import io.vproxy.base.processor.http1.entity.Header;

public class HeaderBuilder implements IHttpHeader {
    public StringBuilder key = new StringBuilder();
    public StringBuilder value = new StringBuilder();

    public Header build() {
        Header h = new Header();
        h.key = key.toString();
        h.value = value.toString();
        return h;
    }

    @Override
    public String toString() {
        return "HeaderBuilder{" +
               "key=" + key +
               ", value=" + value +
               '}';
    }

    @Override
    public String keyAsString() {
        return key.toString();
    }

    @Override
    public StringBuilder keyAsStringBuilder() {
        return key;
    }

    @Override
    public byte[] keyAsBytes() {
        return key.toString().getBytes();
    }

    @Override
    public String valueAsString() {
        return value.toString();
    }

    @Override
    public StringBuilder valueAsStringBuilder() {
        return value;
    }

    @Override
    public byte[] valueAsBytes() {
        return value.toString().getBytes();
    }

    @Override
    public boolean keyEqualsIgnoreCase(String key) {
        if (this.key.length() != key.length())
            return false;
        return this.key.toString().equalsIgnoreCase(key);
    }
}
