package vserver;

import vproxy.dns.Resolver;
import vserver.server.Http1ServerImpl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public interface HttpServer {
    static HttpServer create() {
        return new Http1ServerImpl();
    }

    default HttpServer get(String route, RoutingHandler handler) {
        return handle(HttpMethod.GET, route, handler);
    }

    default HttpServer pst(String route, RoutingHandler handler) {
        return handle(HttpMethod.POST, route, handler);
    }

    default HttpServer put(String route, RoutingHandler handler) {
        return handle(HttpMethod.PUT, route, handler);
    }

    default HttpServer del(String route, RoutingHandler handler) {
        return handle(HttpMethod.DELETE, route, handler);
    }

    default HttpServer all(String route, RoutingHandler handler) {
        return handle(HttpMethod.ALL_METHODS, route, handler);
    }

    default HttpServer handle(HttpMethod method, String route, RoutingHandler handler) {
        return handle(new HttpMethod[]{method}, Route.create(route), handler);
    }

    default HttpServer handle(HttpMethod[] methods, String route, RoutingHandler handler) {
        return handle(methods, Route.create(route), handler);
    }

    HttpServer handle(HttpMethod[] methods, Route route, RoutingHandler handler);

    default void listen(int port) throws IOException {
        listen(port, "0.0.0.0");
    }

    default void listenIPv6(int port) throws IOException {
        listen(port, "::");
    }

    default void listen(int port, String address) throws IOException {
        InetAddress l3addr;
        try {
            l3addr = Resolver.getDefault().blockResolve(address);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        listen(port, l3addr);
    }

    default void listen(int port, InetAddress addr) throws IOException {
        listen(new InetSocketAddress(addr, port));
    }

    void listen(InetSocketAddress addr) throws IOException;

    void close();
}
