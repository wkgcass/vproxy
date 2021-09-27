package io.vproxy.base.processor.http;

import io.vproxy.base.processor.ConnectionDelegate;
import io.vproxy.base.processor.Processor;
import io.vproxy.base.processor.http1.HttpProcessor;
import io.vproxy.base.processor.httpbin.BinaryHttpProcessor;
import io.vproxy.base.processor.httpbin.BinaryHttpSubContext;
import io.vproxy.base.processor.httpbin.HttpVersion;
import io.vproxy.base.util.ByteArray;
import io.vproxy.vfd.IPPort;

public class GeneralHttpProcessor implements Processor<GeneralHttpContext, GeneralHttpSubContext> {
    private final HttpProcessor httpProcessor = new HttpProcessor();
    private final BinaryHttpProcessor http2Processor = new BinaryHttpProcessor(HttpVersion.HTTP2);

    @Override
    public String name() {
        return "http";
    }

    @Override
    public String[] alpn() {
        String[] h2 = http2Processor.alpn();
        String[] h1 = httpProcessor.alpn();
        String[] ret = new String[h1.length + h2.length];
        System.arraycopy(h2, 0, ret, 0, h2.length);
        System.arraycopy(h1, 0, ret, h2.length, h1.length);
        return ret;
    }

    @Override
    public GeneralHttpContext init(IPPort clientAddress) {
        return new GeneralHttpContext(httpProcessor.init(clientAddress), http2Processor.init(clientAddress));
    }

    @Override
    public GeneralHttpSubContext initSub(GeneralHttpContext ctx, int id, ConnectionDelegate delegate) {
        return new GeneralHttpSubContext(
            id,
            httpProcessor.initSub(ctx.httpContext, id, delegate),
            http2Processor.initSub(ctx.http2Context, id, delegate)
        );
    }

    @Override
    public ProcessorTODO process(GeneralHttpContext ctx, GeneralHttpSubContext subCtx) {
        if (ctx.useHttp) return httpProcessor.process(ctx.httpContext, subCtx.httpSubContext);
        if (ctx.useHttp2) return http2Processor.process(ctx.http2Context, subCtx.http2SubContext);
        if (ctx.willUseHttp2) {
            ProcessorTODO ret = http2Processor.process(ctx.http2Context, subCtx.http2SubContext);
            ret.mode = Mode.handle;
            assert ret.len == BinaryHttpSubContext.H2_PREFACE.length();
            ret.len = ret.len - 2;
            var feedFunc = ret.feed;
            ret.feed = data -> {
                ctx.useHttp2 = true;
                return feedFunc.apply(ByteArray.from("PR".getBytes()).concat(data));
            };
            return ret;
        }
        // try to determine http/1.x or http/2 by the first two bytes
        ProcessorTODO processorTODO = ProcessorTODO.create();
        processorTODO.mode = Mode.handle;
        processorTODO.len = 2;
        processorTODO.feed = data -> {
            if (data.get(0) == 'P' && data.get(1) == 'R') {
                ctx.willUseHttp2 = true;
                return null;
            } else {
                ctx.useHttp = true;
                // feed the h1 processor with these two bytes
                return httpProcessor.process(ctx.httpContext, subCtx.httpSubContext).feed.apply(data);
            }
        };
        return processorTODO;
    }

    @Override
    public HandleTODO connected(GeneralHttpContext ctx, GeneralHttpSubContext subCtx) {
        if (ctx.useHttp) return httpProcessor.connected(ctx.httpContext, subCtx.httpSubContext);
        if (ctx.useHttp2) return http2Processor.connected(ctx.http2Context, subCtx.http2SubContext);
        // if (ctx.willUseHttp2)
        return null;
    }

    @Override
    public HandleTODO remoteClosed(GeneralHttpContext ctx, GeneralHttpSubContext subCtx) {
        if (ctx.useHttp) return httpProcessor.remoteClosed(ctx.httpContext, subCtx.httpSubContext);
        if (ctx.useHttp2) return http2Processor.remoteClosed(ctx.http2Context, subCtx.http2SubContext);
        // if (ctx.willUseHttp2)
        return null;
    }

    @Override
    public DisconnectTODO disconnected(GeneralHttpContext ctx, GeneralHttpSubContext subCtx, boolean exception) {
        if (ctx.useHttp) return httpProcessor.disconnected(ctx.httpContext, subCtx.httpSubContext, exception);
        if (ctx.useHttp2) return http2Processor.disconnected(ctx.http2Context, subCtx.http2SubContext, exception);
        // if (ctx.willUseHttp2)
        return null;
    }
}
