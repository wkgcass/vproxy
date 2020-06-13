package vproxybase.processor.http;

import vproxybase.processor.Processor;
import vproxybase.processor.http1.HttpSubContext;
import vproxybase.processor.http2.Http2SubContext;

public class GeneralHttpSubContext extends Processor.SubContext {
    final int connId;
    final HttpSubContext httpSubContext;
    final Http2SubContext http2SubContext;

    public GeneralHttpSubContext(int connId, HttpSubContext httpSubContext, Http2SubContext http2SubContext) {
        super(connId);
        this.connId = connId;
        this.httpSubContext = httpSubContext;
        this.http2SubContext = http2SubContext;
    }
}
