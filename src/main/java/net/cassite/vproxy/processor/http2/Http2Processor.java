package net.cassite.vproxy.processor.http2;


import net.cassite.vproxy.component.proxy.Processor;

public class Http2Processor implements Processor<Http2Context, Http2SubContext> {
    @Override
    public Http2Context init() {
        return new Http2Context();
    }

    @Override
    public Http2SubContext initSub(Http2Context ctx, int id) {
        return new Http2SubContext(ctx, id);
    }

    @Override
    public Mode mode(Http2Context ctx, Http2SubContext subCtx) {
        return subCtx.mode();
    }

    @Override
    public int len(Http2Context ctx, Http2SubContext subCtx) {
        return subCtx.len();
    }

    @Override
    public byte[] feed(Http2Context ctx, Http2SubContext subCtx, byte[] data) throws Exception {
        return subCtx.feed(data);
    }

    @Override
    public void proxyDone(Http2Context ctx, Http2SubContext subCtx) {
        subCtx.proxyDone();
    }

    @Override
    public int connection(Http2Context ctx, Http2SubContext front) {
        return ctx.connection(front);
    }

    @Override
    public void chosen(Http2Context ctx, Http2SubContext front, Http2SubContext subCtx) {
        ctx.chosen(front, subCtx);
    }

    @Override
    public byte[] connected(Http2Context ctx, Http2SubContext subCtx) {
        return subCtx.connected();
    }
}
