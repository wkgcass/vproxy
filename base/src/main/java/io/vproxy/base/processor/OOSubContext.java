package io.vproxy.base.processor;

public abstract class OOSubContext<CTX extends OOContext> extends Processor.SubContext {
    public final CTX ctx;
    public final int connId;
    public final ConnectionDelegate delegate;

    public OOSubContext(CTX ctx, int connId, ConnectionDelegate delegate) {
        super(connId);
        this.ctx = ctx;
        this.connId = connId;
        this.delegate = delegate;

        if (connId == 0) {
            ctx.frontendSubCtx = this;
        }
    }

    public abstract Processor.ProcessorTODO process();

    public abstract Processor.HandleTODO connected();

    public abstract Processor.HandleTODO remoteClosed();

    public abstract Processor.DisconnectTODO disconnected(boolean exception);
}
