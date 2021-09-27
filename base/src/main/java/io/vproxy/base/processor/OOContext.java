package vproxy.base.processor;

public abstract class OOContext<SUB extends OOSubContext> extends Processor.Context {
    protected SUB frontendSubCtx;

    public OOContext() {
    }
}
