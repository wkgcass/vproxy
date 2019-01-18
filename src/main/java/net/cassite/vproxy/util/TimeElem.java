package net.cassite.vproxy.util;

public class TimeElem<T> {
    public final long triggerTime;
    public final T elem;
    private final TimeQueue<T> queue;

    TimeElem(long triggerTime, T elem, TimeQueue<T> queue) {
        this.triggerTime = triggerTime;
        this.elem = elem;
        this.queue = queue;
    }

    public void removeSelf() {
        queue.list.remove(this);
    }
}
