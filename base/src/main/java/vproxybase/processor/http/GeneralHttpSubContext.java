package vproxybase.processor.http;

import vproxybase.processor.Processor;
import vproxybase.processor.http1.HttpSubContext;
import vproxybase.processor.httpbin.BinaryHttpSubContext;
import vproxybase.processor.httpbin.BinaryHttpSubContextCaster;

public class GeneralHttpSubContext extends Processor.SubContext implements BinaryHttpSubContextCaster {
    final HttpSubContext httpSubContext;
    final BinaryHttpSubContext http2SubContext;

    public GeneralHttpSubContext(int connId, HttpSubContext httpSubContext, BinaryHttpSubContext http2SubContext) {
        super(connId);
        this.httpSubContext = httpSubContext;
        this.http2SubContext = http2SubContext;
    }

    @Override
    public BinaryHttpSubContext castToBinaryHttpSubContext() {
        return http2SubContext;
    }
}
