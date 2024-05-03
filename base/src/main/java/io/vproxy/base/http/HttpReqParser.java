package io.vproxy.base.http;

import io.vproxy.base.processor.DummyConnectionDelegate;
import io.vproxy.base.processor.Processor;
import io.vproxy.base.processor.ProcessorProvider;
import io.vproxy.base.processor.http1.HttpSubContext;
import io.vproxy.base.processor.http1.entity.Request;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.codec.AbstractParser;

import java.util.Set;

public class HttpReqParser extends AbstractParser<Request> {
    private final boolean parseBody;
    private final HttpSubContext ctx;

    public HttpReqParser(boolean parseBody) {
        super(Set.of(1, 2));
        result = null;
        this.parseBody = parseBody;

        Processor p = ProcessorProvider.getInstance().get("http/1.x");
        Processor.Context c = p.init(null);
        //noinspection unchecked
        Processor.SubContext s = p.initSub(new Processor.SubContextInitParams<>(
            c, 0, DummyConnectionDelegate.getInstance()
        ));
        ctx = (HttpSubContext) s;
        ctx.setParserMode();
    }

    @Override
    protected int doSwitch(byte b) {
        try {
            ctx.feed(b);
        } catch (Exception e) {
            // got error when parsing
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "parse http failed: " + e);
            errorMessage = e.getMessage();
            return -1;
        }
        if (ctx.isIdle()) {
            result = ctx.getReq();
            state = 1;
        } else if (!parseBody && ctx.isBeforeBody()) {
            result = ctx.getReq();
            state = 2;
        }
        return state;
    }
}
