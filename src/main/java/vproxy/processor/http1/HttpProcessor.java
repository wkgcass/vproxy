package vproxy.processor.http1;

import vproxy.processor.OOProcessor;

import java.net.InetSocketAddress;

public class HttpProcessor extends OOProcessor<HttpContext, HttpSubContext> {
    @Override
    public String name() {
        return "http/1.x";
    }

    @Override
    public String[] alpn() {
        return new String[]{"http/1.1", "http/1.0"};
    }

    @Override
    public HttpContext init(InetSocketAddress clientAddress) {
        return new HttpContext(clientAddress);
    }

    @Override
    public HttpSubContext initSub(HttpContext httpContext, int id, InetSocketAddress associatedAddress) {
        return new HttpSubContext(httpContext, id);
    }
}
