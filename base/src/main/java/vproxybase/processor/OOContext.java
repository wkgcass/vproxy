package vproxybase.processor;

public abstract class OOContext<SUB extends OOSubContext> extends Processor.Context {
    public OOContext() {
    }

    public abstract int connection(SUB front);

    public abstract Hint connectionHint(SUB front);

    public abstract void chosen(SUB front, SUB subCtx);
}
