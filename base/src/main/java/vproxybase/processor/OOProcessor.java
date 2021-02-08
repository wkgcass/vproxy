package vproxybase.processor;

public abstract class OOProcessor<CTX extends OOContext<SUB>, SUB extends OOSubContext> implements Processor<CTX, SUB> {
    @Override
    public ProcessorTODO process(CTX ctx, SUB sub) {
        return sub.process();
    }

    @Override
    public HandleTODO connected(CTX ctx, SUB sub) {
        return sub.connected();
    }

    @Override
    public HandleTODO remoteClosed(CTX ctx, SUB sub) {
        return sub.remoteClosed();
    }

    @Override
    public DisconnectTODO disconnected(CTX ctx, SUB sub, boolean exception) {
        return sub.disconnected(exception);
    }
}
