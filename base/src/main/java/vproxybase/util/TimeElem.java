package vproxybase.util;

public class TimeElem<T> {
    public final long triggerTime;
    public final T elem;
    private final TimeQueue<T> queue;

    TimeElem(long triggerTime, T elem, TimeQueue<T> queue) {
        this.triggerTime = triggerTime;
        this.elem = elem;
        this.queue = queue;
    }

    // this method should always be called on the event loop
    public void removeSelf() {
        queue.queue.remove(this);
    }
}
