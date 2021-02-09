package vproxybase.processor.http1;

import vfd.IPPort;
import vproxybase.processor.ConnectionDelegate;
import vproxybase.processor.OOProcessor;

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
    public HttpContext init(IPPort clientAddress) {
        return new HttpContext(clientAddress);
    }

    @Override
    public HttpSubContext initSub(HttpContext httpContext, int id, ConnectionDelegate delegate) {
        return new HttpSubContext(httpContext, id, delegate);
    }
}
