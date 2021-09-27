package io.vproxy.base.http;

import io.vproxy.base.processor.DummyConnectionDelegate;
import io.vproxy.base.processor.Processor;
import io.vproxy.base.processor.ProcessorProvider;
import io.vproxy.base.processor.httpbin.BinaryHttpSubContext;
import io.vproxy.base.processor.httpbin.HttpFrame;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.RingBuffer;
import io.vproxy.base.util.codec.AbstractDecoder;

import java.util.Set;

public class Http2Decoder extends AbstractDecoder<HttpFrame> {
    private final BinaryHttpSubContext ctx;

    public Http2Decoder(boolean decodeDataFromServer /* i.e. run as client */) {
        super(
            // terminate states
            Set.of(
                BinaryHttpSubContext.STATE_FIRST_SETTINGS_FRAME_HEADER, // produces Preface
                BinaryHttpSubContext.STATE_FRAME_HEADER, // produces some frame
                BinaryHttpSubContext.STATE_CONTINUATION_FRAME_HEADER // produces header frame
            ),
            Set.of(
                BinaryHttpSubContext.STATE_FIRST_SETTINGS_FRAME_HEADER,
                BinaryHttpSubContext.STATE_FRAME_HEADER,
                BinaryHttpSubContext.STATE_CONTINUATION_FRAME_HEADER
            ));
        Processor p = ProcessorProvider.getInstance().get("h2");
        Processor.Context ctx = p.init(null);
        //noinspection unchecked
        Processor.SubContext sub = p.initSub(ctx, decodeDataFromServer ? 1 : 0, DummyConnectionDelegate.getInstance());
        this.ctx = (BinaryHttpSubContext) sub;
        this.ctx.setParserMode();
    }

    @Override
    protected int doDecode(RingBuffer inBuffer) {
        try {
            ctx.feed(inBuffer);
        } catch (Exception e) {
            assert Logger.lowLevelDebug("decode http2 got exception " + e);
            assert Logger.printStackTrace(e);
            String errMsg = e.getMessage();
            if (errMsg == null) {
                errorMessage = e.toString();
            } else {
                errorMessage = errMsg;
            }
            return -1;
        }
        result = ctx.getFrame();
        if (ctx.isIdle()) {
            return ctx.getState();
        } else {
            return 1000 + ctx.getState();
        }
    }

    public BinaryHttpSubContext getCtx() {
        return ctx;
    }
}
