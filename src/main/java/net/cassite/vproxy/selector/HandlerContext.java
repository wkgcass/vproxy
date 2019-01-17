package net.cassite.vproxy.selector;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

public class HandlerContext<CHANNEL extends SelectableChannel> {
    private final SelectorEventLoop eventLoop;
    CHANNEL channel;
    Object attachment;

    HandlerContext(SelectorEventLoop eventLoop) {
        this.eventLoop = eventLoop;
    }

    public void remove() {
        eventLoop.remove(channel);
    }

    public void modify(int ops) {
        eventLoop.modify(channel, ops);
    }

    public void addOps(int ops) {
        eventLoop.addOps(channel, ops);
    }

    public void rmOps(int ops) {
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

    public int getOps() {
        SelectionKey key = channel.keyFor(eventLoop.selector);
        if (key == null)
            return 0; // it is not registered
        return key.interestOps();
    }
}
