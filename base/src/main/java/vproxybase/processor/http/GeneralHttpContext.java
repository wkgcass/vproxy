package vproxybase.processor.http;

import vproxybase.processor.Processor;
import vproxybase.processor.http1.HttpContext;
import vproxybase.processor.httpbin.BinaryHttpContext;

public class GeneralHttpContext extends Processor.Context {
    final HttpContext httpContext;
    final BinaryHttpContext http2Context;
    boolean useHttp;
    boolean useHttp2;
    boolean willUseHttp2;

    public GeneralHttpContext(HttpContext httpContext, BinaryHttpContext http2Context) {
        this.httpContext = httpContext;
        this.http2Context = http2Context;
    }
}
