package vproxy.vserver;

import vproxy.vserver.impl.Http1ServerImpl;

public interface HttpServer extends GeneralServer {
    static HttpServer create() {
        return new Http1ServerImpl();
    }

    default HttpServer get(String route, RoutingHandler handler) {
        return handle(HttpMethod.GET, route, handler);
    }

    default HttpServer pst(String route, RoutingHandler handler) {
        return handle(HttpMethod.POST, route, handler);
    }

    default HttpServer post(String route, RoutingHandler handler) {
        return pst(route, handler);
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
        return handle(new HttpMethod[]{method}, SubPath.create(route), handler);
    }

    default HttpServer handle(HttpMethod[] methods, String route, RoutingHandler handler) {
        return handle(methods, SubPath.create(route), handler);
    }

    HttpServer handle(HttpMethod[] methods, SubPath route, RoutingHandler handler);
}
