package vproxy.processor;

import vproxy.util.ByteArray;

public abstract class OOSubContext<CTX extends OOContext> extends Processor.SubContext {
    public final CTX ctx;
    public final int connId;

    public OOSubContext(CTX ctx, int connId) {
        this.ctx = ctx;
        this.connId = connId;
    }

    public abstract Processor.Mode mode();

    public abstract boolean expectNewFrame();

    public abstract int len();

    public abstract ByteArray feed(ByteArray data) throws Exception;

    public abstract ByteArray produce();

    public abstract void proxyDone();

    public abstract ByteArray connected();
}
