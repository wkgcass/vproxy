package vproxy.processor.http;

import vproxy.processor.Processor;
import vproxy.processor.http1.HttpProcessor;
import vproxy.processor.http2.Http2Processor;
import vproxy.processor.http2.Http2SubContext;
import vproxy.util.ByteArray;

import java.net.InetSocketAddress;

public class GeneralHttpProcessor implements Processor<GeneralHttpContext, GeneralHttpSubContext> {
    private final HttpProcessor httpProcessor = new HttpProcessor();
    private final Http2Processor http2Processor = new Http2Processor();

    @Override
    public String name() {
        return "http";
    }

    @Override
    public GeneralHttpContext init(InetSocketAddress clientAddress) {
        return new GeneralHttpContext(httpProcessor.init(clientAddress), http2Processor.init(clientAddress));
    }

    @Override
    public GeneralHttpSubContext initSub(GeneralHttpContext ctx, int id, InetSocketAddress associatedAddress) {
        return new GeneralHttpSubContext(
            id,
            httpProcessor.initSub(ctx.httpContext, id, associatedAddress),
            http2Processor.initSub(ctx.http2Context, id, associatedAddress)
        );
    }

    @Override
    public Mode mode(GeneralHttpContext ctx, GeneralHttpSubContext subCtx) {
        if (ctx.useHttp) return httpProcessor.mode(ctx.httpContext, subCtx.httpSubContext);
        if (ctx.useHttp2) return http2Processor.mode(ctx.http2Context, subCtx.http2SubContext);
        // if (ctx.willUseHttp2)
        return Mode.handle;
    }

    @Override
    public boolean expectNewFrame(GeneralHttpContext ctx, GeneralHttpSubContext subCtx) {
        if (ctx.useHttp) return httpProcessor.expectNewFrame(ctx.httpContext, subCtx.httpSubContext);
        if (ctx.useHttp2) return http2Processor.expectNewFrame(ctx.http2Context, subCtx.http2SubContext);
        // if (ctx.willUseHttp2)
        return false;
    }

    @Override
    public int len(GeneralHttpContext ctx, GeneralHttpSubContext subCtx) {
        if (ctx.useHttp) return httpProcessor.len(ctx.httpContext, subCtx.httpSubContext);
        if (ctx.useHttp2) return http2Processor.len(ctx.http2Context, subCtx.http2SubContext);
        if (ctx.willUseHttp2) {
            // NOTE: this is the same as (Http2SubContext#len when state == 0) - (the bytes to determine h1/h2);
            return Http2SubContext.SEQ_PREFACE_MAGIC.length() + Http2SubContext.LEN_FRAME_HEAD - 2;
        }
        return 2; // we only need two bytes to determine whether its h2 or h1
    }

    @Override
    public ByteArray feed(GeneralHttpContext ctx, GeneralHttpSubContext subCtx, ByteArray data) throws Exception {
        if (ctx.useHttp) return httpProcessor.feed(ctx.httpContext, subCtx.httpSubContext, data);
        if (ctx.useHttp2) return http2Processor.feed(ctx.http2Context, subCtx.http2SubContext, data);
        if (ctx.willUseHttp2) {
            ctx.useHttp2 = true;
            ByteArray ret = http2Processor.feed(ctx.http2Context, subCtx.http2SubContext, ByteArray.from("PR".getBytes()).concat(data));
            http2Processor.chosen(ctx.http2Context, subCtx.http2SubContext, ctx.chosen.http2SubContext);
            ctx.chosen = null; // release the context, make it possible for gc to process
            return ret;
        }
        if (data.get(0) == 'P' && data.get(1) == 'R') {
            ctx.willUseHttp2 = true;
            return null;
        } else {
            ctx.useHttp = true;
            // feed the h1 processor with these two bytes
            httpProcessor.feed(ctx.httpContext, subCtx.httpSubContext, data.sub(0, 1));
            httpProcessor.feed(ctx.httpContext, subCtx.httpSubContext, data.sub(1, 1));
            return data;
        }
    }

    @Override
    public ByteArray produce(GeneralHttpContext ctx, GeneralHttpSubContext subCtx) {
        if (ctx.useHttp) return httpProcessor.produce(ctx.httpContext, subCtx.httpSubContext);
        if (ctx.useHttp2) return http2Processor.produce(ctx.http2Context, subCtx.http2SubContext);
        // if (ctx.willUseHttp2)
        return null;
    }

    @Override
    public void proxyDone(GeneralHttpContext ctx, GeneralHttpSubContext subCtx) {
        if (ctx.useHttp) httpProcessor.proxyDone(ctx.httpContext, subCtx.httpSubContext);
        if (ctx.useHttp2) http2Processor.proxyDone(ctx.http2Context, subCtx.http2SubContext);
        // will not happen when not protocol not learned
    }

    @Override
    public int connection(GeneralHttpContext ctx, GeneralHttpSubContext subCtx) {
        if (ctx.useHttp) return httpProcessor.connection(ctx.httpContext, subCtx.httpSubContext);
        if (ctx.useHttp2) return http2Processor.connection(ctx.http2Context, subCtx.http2SubContext);
        // if (ctx.willUseHttp2)
        return ctx.chosen == null ? -1 : ctx.chosen.connId;
    }

    @Override
    public void chosen(GeneralHttpContext ctx, GeneralHttpSubContext front, GeneralHttpSubContext subCtx) {
        if (ctx.useHttp) httpProcessor.chosen(ctx.httpContext, front.httpSubContext, subCtx.httpSubContext);
        if (ctx.useHttp2) http2Processor.chosen(ctx.http2Context, front.http2SubContext, subCtx.http2SubContext);
        // if (ctx.willUseHttp2)
        ctx.chosen = subCtx;
    }

    @Override
    public ByteArray connected(GeneralHttpContext ctx, GeneralHttpSubContext subCtx) {
        if (ctx.useHttp) return httpProcessor.connected(ctx.httpContext, subCtx.httpSubContext);
        if (ctx.useHttp2) return http2Processor.connected(ctx.http2Context, subCtx.http2SubContext);
        // if (ctx.willUseHttp2)
        return null;
    }
}
