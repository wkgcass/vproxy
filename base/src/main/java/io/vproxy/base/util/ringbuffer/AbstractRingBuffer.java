package io.vproxy.base.util.ringbuffer;

import io.vproxy.base.util.RingBuffer;
import io.vproxy.base.util.RingBufferETHandler;

import java.util.HashSet;
import java.util.Set;

public abstract class AbstractRingBuffer implements RingBuffer {
    private boolean operating = false;
    private final Set<RingBufferETHandler> handler = new HashSet<>();
    private final Set<RingBufferETHandler> handlerToAdd = new HashSet<>();
    private final Set<RingBufferETHandler> handlerToRemove = new HashSet<>();

    protected boolean isOperating() {
        return operating;
    }

    protected void setOperating(boolean operating) {
        if (this.operating && !operating) {
            handler.removeAll(handlerToRemove);
            handler.addAll(handlerToAdd);
        }
        this.operating = operating;
    }

    protected void triggerReadable() {
        for (RingBufferETHandler aHandler : handler) {
            aHandler.readableET();
        }
    }

    protected void triggerWritable() {
        for (RingBufferETHandler aHandler : handler) {
            aHandler.writableET();
        }
    }

    @Override
    public void addHandler(RingBufferETHandler h) {
        if (operating) {
            handlerToRemove.remove(h);
            handlerToAdd.add(h);
        } else {
            handler.add(h);
        }
    }

    @Override
    public void removeHandler(RingBufferETHandler h) {
        if (operating) {
            handlerToAdd.remove(h);
            handlerToRemove.add(h);
        } else {
            handler.remove(h);
        }
    }

    @Override
    public Set<RingBufferETHandler> getHandlers() {
        return new HashSet<>(handler);
    }
}
