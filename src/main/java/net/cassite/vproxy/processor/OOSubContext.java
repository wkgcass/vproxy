package net.cassite.vproxy.processor;

import net.cassite.vproxy.component.proxy.Processor;

public abstract class OOSubContext<CTX extends OOContext> extends Processor.SubContext {
    public final CTX ctx;
    public final int connId;

    public OOSubContext(CTX ctx, int connId) {
        this.ctx = ctx;
        this.connId = connId;
    }

    public abstract Processor.Mode mode();

    public abstract int len();

    public abstract byte[] feed(byte[] data) throws Exception;

    public abstract byte[] produce();

    public abstract void proxyDone();

    public abstract byte[] connected();
}
