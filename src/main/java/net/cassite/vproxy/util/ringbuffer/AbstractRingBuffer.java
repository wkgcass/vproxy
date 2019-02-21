package net.cassite.vproxy.util.ringbuffer;

import net.cassite.vproxy.util.RingBuffer;
import net.cassite.vproxy.util.RingBufferETHandler;

import java.util.HashSet;
import java.util.Set;

public abstract class AbstractRingBuffer implements RingBuffer {
    private boolean operating = false;
    private Set<RingBufferETHandler> handler = new HashSet<>();
    private Set<RingBufferETHandler> handlerToAdd = new HashSet<>();
    private Set<RingBufferETHandler> handlerToRemove = new HashSet<>();

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
}
