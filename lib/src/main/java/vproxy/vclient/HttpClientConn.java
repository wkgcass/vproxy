package vproxy.vclient;

import vproxy.vlibbase.ConnRef;
import vproxy.vserver.HttpMethod;

public interface HttpClientConn extends ConnRef {
    default HttpRequest get(String uri) {
        return request(HttpMethod.GET, uri);
    }

    default HttpRequest pst(String uri) {
        return request(HttpMethod.POST, uri);
    }

    default HttpRequest post(String uri) {
        return pst(uri);
    }

    default HttpRequest put(String uri) {
        return request(HttpMethod.PUT, uri);
    }

    default HttpRequest del(String uri) {
        return request(HttpMethod.DELETE, uri);
    }

    HttpRequest request(HttpMethod method, String uri);
}
