package vproxy.processor.http1;

import vproxy.processor.OOProcessor;

import java.net.InetSocketAddress;

public class HttpProcessor extends OOProcessor<HttpContext, HttpSubContext> {
    @Override
    public String name() {
        return "http";
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
