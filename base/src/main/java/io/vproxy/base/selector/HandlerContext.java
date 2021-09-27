package vproxy.base.selector;

import vproxy.vfd.EventSet;
import vproxy.vfd.FD;

public class HandlerContext<CHANNEL extends FD> {
    private final SelectorEventLoop eventLoop;
    CHANNEL channel;
    Object attachment;

    HandlerContext(SelectorEventLoop eventLoop) {
        this.eventLoop = eventLoop;
    }

    public void remove() {
        eventLoop.remove(channel);
    }

    public void modify(EventSet ops) {
        eventLoop.modify(channel, ops);
    }

    public void addOps(EventSet ops) {
        eventLoop.addOps(channel, ops);
    }

    public void rmOps(EventSet ops) {
        eventLoop.rmOps(channel, ops);
    }

    public SelectorEventLoop getEventLoop() {
        return eventLoop;
    }

    public CHANNEL getChannel() {
        return channel;
    }

    public Object getAttachment() {
        return attachment;
    }

    public EventSet getOps() {
        return eventLoop.getOps(channel);
    }
}
