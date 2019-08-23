package vserver;

public enum HttpMethod {
    GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH, CONNECT, TRACE;

    public static final HttpMethod[] ALL_METHODS = new HttpMethod[]{
        GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH, CONNECT, TRACE
    };
}
