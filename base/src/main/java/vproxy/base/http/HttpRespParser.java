package vproxy.base.http;

import vproxy.base.processor.DummyConnectionDelegate;
import vproxy.base.processor.Processor;
import vproxy.base.processor.ProcessorProvider;
import vproxy.base.processor.http1.HttpSubContext;
import vproxy.base.processor.http1.entity.Response;
import vproxy.base.util.LogType;
import vproxy.base.util.Logger;
import vproxy.base.util.codec.AbstractParser;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class HttpRespParser extends AbstractParser<Response> {
    private final boolean parseBody;
    private final HttpSubContext ctx;

    public HttpRespParser(boolean parseBody) {
        super(new HashSet<>(Arrays.asList(1, 2)), Collections.singleton(2));
        result = null;
        this.parseBody = parseBody;

        Processor p = ProcessorProvider.getInstance().get("http/1.x");
        Processor.Context c = p.init(null);
        //noinspection unchecked
        Processor.SubContext s = p.initSub(c, 1, DummyConnectionDelegate.getInstance());
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
            result = ctx.getResp();
            return 1;
        } else if (!parseBody && ctx.isBeforeBody()) {
            result = ctx.getResp();
            return 2;
        }
        return state;
    }
}
