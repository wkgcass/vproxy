package vproxy.processor;

import vproxy.util.ByteArray;

public abstract class OOProcessor<CTX extends OOContext<SUB>, SUB extends OOSubContext> implements Processor<CTX, SUB> {
    @Override
    public Mode mode(CTX ctx, SUB sub) {
        return sub.mode();
    }

    @Override
    public boolean expectNewFrame(CTX ctx, SUB sub) {
        return sub.expectNewFrame();
    }

    @Override
    public int len(CTX ctx, SUB sub) {
        return sub.len();
    }

    @Override
    public ByteArray feed(CTX ctx, SUB sub, ByteArray data) throws Exception {
        return sub.feed(data);
    }

    @Override
    public ByteArray produce(CTX ctx, SUB sub) {
        return sub.produce();
    }

    @Override
    public void proxyDone(CTX ctx, SUB sub) {
        sub.proxyDone();
    }

    @Override
    public int connection(CTX ctx, SUB front) {
        return ctx.connection(front);
    }

    @Override
    public void chosen(CTX ctx, SUB front, SUB sub) {
        ctx.chosen(front, sub);
    }

    @Override
    public ByteArray connected(CTX ctx, SUB sub) {
        return sub.connected();
    }
}
