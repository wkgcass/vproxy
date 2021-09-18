package vproxy.base.util.time.impl;

import vproxy.base.util.time.TimeElem;

public class TimeElemImpl<T> implements TimeElem<T> {
    public final long triggerTime;
    public final T elem;
    private final TimeQueueImpl<T> queue;

    TimeElemImpl(long triggerTime, T elem, TimeQueueImpl<T> queue) {
        this.triggerTime = triggerTime;
        this.elem = elem;
        this.queue = queue;
    }

    @Override
    public T get() {
        return elem;
    }

    @Override
    public void removeSelf() {
        queue.remove(this);
    }
}
