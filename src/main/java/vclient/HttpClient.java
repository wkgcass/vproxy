package vclient;

import vclient.impl.Http1ClientImpl;
import vserver.HttpMethod;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public interface HttpClient {
    static HttpClient to(String host, int port) {
        try {
            return to(InetAddress.getByName(host), port);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    static HttpClient to(InetAddress l3addr, int port) {
        return to(new InetSocketAddress(l3addr, port));
    }

    static HttpClient to(InetSocketAddress l4addr) {
        return new Http1ClientImpl(l4addr);
    }

    default HttpRequest get(String uri) {
        return request(HttpMethod.GET, uri);
    }

    default HttpRequest pst(String uri) {
        return request(HttpMethod.POST, uri);
    }

    default HttpRequest put(String uri) {
        return request(HttpMethod.PUT, uri);
    }

    default HttpRequest del(String uri) {
        return request(HttpMethod.DELETE, uri);
    }

    HttpRequest request(HttpMethod method, String uri);

    void close();
}
