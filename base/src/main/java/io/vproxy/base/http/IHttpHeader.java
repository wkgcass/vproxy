package io.vproxy.base.http;

public interface IHttpHeader {
    String keyAsString();

    StringBuilder keyAsStringBuilder();

    byte[] keyAsBytes();

    String valueAsString();

    StringBuilder valueAsStringBuilder();

    byte[] valueAsBytes();

    boolean keyEqualsIgnoreCase(char[] key);

    boolean keyEqualsIgnoreCase(String key);
}
