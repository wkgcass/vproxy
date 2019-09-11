package vserver;

import vproxy.util.ByteArray;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RoutingContext {
    private final HttpMethod method;
    private final String uri;
    private final Map<String, String> query;
    private final Map<String, String> headers;
    private final ByteArray body;
    private final Map<String, String> params = new HashMap<>();
    private final Map<StorageKey, Object> storage = new HashMap<>();
    private final HttpResponse response;
    private final HandlerChain chain;

    @SuppressWarnings("unused")
    public interface StorageKey<T> {
    }

    public RoutingContext(HttpMethod method,
                          String uri,
                          Map<String, String> query,
                          Map<String, String> headers,
                          ByteArray body,
                          HttpResponse response,
                          HandlerChain chain) {
        this.method = method;
        this.uri = uri;
        this.query = Collections.unmodifiableMap(query);
        this.headers = Collections.unmodifiableMap(headers);
        this.body = body;
        this.response = response;
        this.chain = chain;
    }

    public RoutingContext putParam(String key, String value) {
        params.put(key, value);
        return this;
    }

    public String param(String key) {
        return params.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(StorageKey<T> key) {
        return (T) storage.get(key);
    }

    public <T> RoutingContext put(StorageKey<T> key, T value) {
        storage.put(key, value);
        return this;
    }

    public HttpMethod method() {
        return method;
    }

    public String uri() {
        return uri;
    }

    public String query(String key) {
        return query.get(key);
    }

    public String header(String key) {
        return headers.get(key.toLowerCase());
    }

    public ByteArray body() {
        return body;
    }

    public void next() {
        chain.next();
    }

    public HttpResponse response() {
        return response;
    }
}
