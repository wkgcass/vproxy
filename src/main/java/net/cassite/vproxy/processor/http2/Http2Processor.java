package net.cassite.vproxy.processor.http2;

import net.cassite.vproxy.processor.OOProcessor;

public class Http2Processor extends OOProcessor<Http2Context, Http2SubContext> {
    @Override
    public Http2Context init() {
        return new Http2Context();
    }

    @Override
    public Http2SubContext initSub(Http2Context ctx, int id) {
        return new Http2SubContext(ctx, id);
    }
}
