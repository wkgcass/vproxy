package vproxy.http;

import vproxy.processor.Processor;
import vproxy.processor.ProcessorProvider;
import vproxy.processor.http.HttpSubContext;
import vproxy.processor.http.entity.Response;
import vproxy.util.*;

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

        Processor p = ProcessorProvider.getInstance().get("http");
        Processor.Context c = p.init(null);
        //noinspection unchecked
        Processor.SubContext s = p.initSub(c, 1, null);
        ctx = (HttpSubContext) s;
    }

    @Override
    protected int doSwitch(byte b) {
        try {
            ctx.feed(ByteArray.from(b));
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
