package net.cassite.vproxy.processor.http2;

import net.cassite.vproxy.app.Config;
import net.cassite.vproxy.processor.OOProcessor;

public class Http2Processor extends OOProcessor<Http2Context, Http2SubContext> {
    private static final int HTTP2_ZERO_COPY_THRESHOLD;

    static {
        HTTP2_ZERO_COPY_THRESHOLD =
            Integer.parseInt(System.getProperty("HTTP2_ZERO_COPY_THRESHOLD",
                "" + Config.recommendedMinPayloadLength));
    }

    @Override
    public Http2Context init() {
        return new Http2Context();
    }

    @Override
    public Http2SubContext initSub(Http2Context ctx, int id) {
        return new Http2SubContext(ctx, id);
    }

    @Override
    public int PROXY_ZERO_COPY_THRESHOLD() {
        return HTTP2_ZERO_COPY_THRESHOLD;
    }
}
