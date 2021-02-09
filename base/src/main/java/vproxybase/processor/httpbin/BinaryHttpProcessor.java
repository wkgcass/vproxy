package vproxybase.processor.httpbin;

import vfd.IPPort;
import vproxybase.Config;
import vproxybase.processor.ConnectionDelegate;
import vproxybase.processor.OOProcessor;
import vproxybase.util.Logger;
import vproxybase.util.Utils;

public class BinaryHttpProcessor extends OOProcessor<BinaryHttpContext, BinaryHttpSubContext> {
    private final HttpVersion httpVersion;

    public BinaryHttpProcessor(HttpVersion httpVersion) {
        this.httpVersion = httpVersion;
    }

    @Override
    public String name() {
        return httpVersion == HttpVersion.HTTP2 ? "h2" : "h3";
    }

    @Override
    public String[] alpn() {
        return new String[]{
            httpVersion == HttpVersion.HTTP2 ? "h2" : "h3"
        };
    }

    @Override
    public BinaryHttpContext init(IPPort clientAddress) {
        return new BinaryHttpContext(clientAddress);
    }

    @Override
    public BinaryHttpSubContext initSub(BinaryHttpContext binaryHttpContext, int id, ConnectionDelegate delegate) {
        return new BinaryHttpSubContext(binaryHttpContext, id, delegate);
    }

    private static final int HTTP_BIN_ZERO_COPY_THRESHOLD;

    static {
        // this is only for debug purpose
        {
            String thresholdStr = Utils.getSystemProperty("http_bin_zero_copy_threshold");
            if (thresholdStr == null) {
                HTTP_BIN_ZERO_COPY_THRESHOLD = Config.recommendedMinPayloadLength;
            } else {
                HTTP_BIN_ZERO_COPY_THRESHOLD = Integer.parseInt(thresholdStr);
                Logger.alert("HTTP_BIN_ZERO_COPY_THRESHOLD is set to " + HTTP_BIN_ZERO_COPY_THRESHOLD);
            }
        }
    }

    @Override
    public int PROXY_ZERO_COPY_THRESHOLD() {
        return HTTP_BIN_ZERO_COPY_THRESHOLD;
    }
}
