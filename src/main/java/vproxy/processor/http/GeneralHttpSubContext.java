package vproxy.processor.http;

import vproxy.processor.Processor;
import vproxy.processor.http1.HttpSubContext;
import vproxy.processor.http2.Http2SubContext;

public class GeneralHttpSubContext extends Processor.SubContext {
    final int connId;
    final HttpSubContext httpSubContext;
    final Http2SubContext http2SubContext;

    public GeneralHttpSubContext(int connId, HttpSubContext httpSubContext, Http2SubContext http2SubContext) {
        this.connId = connId;
        this.httpSubContext = httpSubContext;
        this.http2SubContext = http2SubContext;
    }
}
