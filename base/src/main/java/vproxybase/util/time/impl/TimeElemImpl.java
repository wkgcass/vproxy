package vproxybase.util.time.impl;

import vproxybase.util.time.TimeElem;

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

    public void removeSelf() {
        queue.queue.remove(this);
    }
}
