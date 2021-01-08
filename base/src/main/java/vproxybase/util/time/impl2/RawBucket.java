package vproxybase.util.time.impl2;

import java.util.LinkedList;
import java.util.Queue;

public class RawBucket<T> implements Bucket<T> {
    private final LinkedList<TimeElemImpl<T>> list = new LinkedList<>();
    private final Queue<TimeElemImpl<T>> queue;

    public RawBucket(Queue<TimeElemImpl<T>> queue) {
        this.queue = queue;
    }

    @Override
    public void add(TimeElemImpl<T> elem) {
        elem.bucket = this;
        list.add(elem);
    }

    @Override
    public void remove(TimeElemImpl<T> elem) {
        if (elem.bucket == this) {
            list.remove(elem);
        }
    }

    @Override
    public void poll(long timeOffset) {
        queue.addAll(list);
        list.clear();
    }

    @Override
    public boolean isEmpty() {
        return list.isEmpty();
    }
}
