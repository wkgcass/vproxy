package vproxybase.http;

import vproxybase.processor.Processor;
import vproxybase.processor.ProcessorProvider;
import vproxybase.processor.http1.HttpSubContext;
import vproxybase.processor.http1.entity.Request;
import vproxybase.util.AbstractParser;
import vproxybase.util.LogType;
import vproxybase.util.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class HttpReqParser extends AbstractParser<Request> {
    private final boolean parseBody;
    private HttpSubContext ctx;

    public HttpReqParser(boolean parseBody) {
        super(new HashSet<>(Arrays.asList(1, 2)), Collections.singleton(2));
        result = null;
        this.parseBody = parseBody;

        Processor p = ProcessorProvider.getInstance().get("http/1.x");
        Processor.Context c = p.init(null);
        //noinspection unchecked
        Processor.SubContext s = p.initSub(c, 0, null);
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
