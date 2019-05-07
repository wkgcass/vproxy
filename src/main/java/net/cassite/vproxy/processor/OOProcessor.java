package net.cassite.vproxy.processor;

import net.cassite.vproxy.component.proxy.Processor;

public abstract class OOProcessor<CTX extends OOContext<SUB>, SUB extends OOSubContext> implements Processor<CTX, SUB> {
    @Override
    public Mode mode(CTX ctx, SUB sub) {
        return sub.mode();
    }

    @Override
    public int len(CTX ctx, SUB sub) {
        return sub.len();
    }

    @Override
    public byte[] feed(CTX ctx, SUB sub, byte[] data) throws Exception {
        return sub.feed(data);
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
    public byte[] connected(CTX ctx, SUB sub) {
        return sub.connected();
    }
}
