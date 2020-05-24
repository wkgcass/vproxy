package vproxybase.processor.http;

import vproxybase.processor.Processor;
import vproxybase.processor.http1.HttpContext;
import vproxybase.processor.http2.Http2Context;

public class GeneralHttpContext extends Processor.Context {
    final HttpContext httpContext;
    final Http2Context http2Context;
    boolean useHttp;
    boolean useHttp2;
    boolean willUseHttp2;

    // we must record the first chosen sub context for the h2 connection
    // because for the second time backend connection establishes,
    // it will act different from the first time
    GeneralHttpSubContext chosen = null;

    public GeneralHttpContext(HttpContext httpContext, Http2Context http2Context) {
        this.httpContext = httpContext;
        this.http2Context = http2Context;
    }
}
