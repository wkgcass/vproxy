package vproxy.processor.http2;

import vfd.IPPort;
import vproxy.app.Config;
import vproxy.processor.OOProcessor;
import vproxy.util.Logger;

public class Http2Processor extends OOProcessor<Http2Context, Http2SubContext> {
    private static final int HTTP2_ZERO_COPY_THRESHOLD;

    static {
        // this is only for debug purpose
        {
            String thresholdStr = System.getProperty("HTTP2_ZERO_COPY_THRESHOLD");
            if (thresholdStr == null) {
                HTTP2_ZERO_COPY_THRESHOLD = Config.recommendedMinPayloadLength;
            } else {
                HTTP2_ZERO_COPY_THRESHOLD = Integer.parseInt(thresholdStr);
                Logger.alert("HTTP2_ZERO_COPY_THRESHOLD is set to " + HTTP2_ZERO_COPY_THRESHOLD);
            }
        }
    }

    @Override
    public String name() {
        return "h2";
    }

    @Override
    public String[] alpn() {
        return new String[]{"h2"};
    }

    @Override
    public Http2Context init(IPPort clientAddress) {
        return new Http2Context(clientAddress);
    }

    @Override
    public Http2SubContext initSub(Http2Context ctx, int id, IPPort associatedAddress) {
        return new Http2SubContext(ctx, id);
    }

    @Override
    public int PROXY_ZERO_COPY_THRESHOLD() {
        return HTTP2_ZERO_COPY_THRESHOLD;
    }
}
