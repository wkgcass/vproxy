package vproxybase.processor;

import vproxybase.util.ByteArray;

public abstract class OOSubContext<CTX extends OOContext> extends Processor.SubContext {
    public final CTX ctx;
    public final int connId;
    public final ConnectionDelegate delegate;

    public OOSubContext(CTX ctx, int connId, ConnectionDelegate delegate) {
        super(connId);
        this.ctx = ctx;
        this.connId = connId;
        this.delegate = delegate;
    }

    public abstract Processor.Mode mode();

    public abstract boolean expectNewFrame();

    public abstract int len();

    public abstract ByteArray feed(ByteArray data) throws Exception;

    public abstract ByteArray produce();

    public abstract void proxyDone();

    public abstract ByteArray connected();

    public abstract ByteArray remoteClosed();

    public abstract boolean disconnected(boolean exception);
}
