package vproxybase.util.time.impl;

import vproxybase.util.time.TimeElem;
import vproxybase.util.time.TimeQueue;

import java.util.PriorityQueue;

public class TimeQueueImpl<T> implements TimeQueue<T> {
    PriorityQueue<TimeElemImpl<T>> queue = new PriorityQueue<>((a, b) -> (int) (a.triggerTime - b.triggerTime));

    @Override
    public TimeElem<T> add(long currentTimestamp, int timeout, T elem) {
        TimeElemImpl<T> event = new TimeElemImpl<>(currentTimestamp + timeout, elem, this);
        queue.add(event);
        return event;
    }

    @Override
    public T poll() {
        TimeElemImpl<T> elem = queue.poll();
        if (elem == null)
            return null;
        return elem.elem;
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public int nextTime(long currentTimestamp) {
        TimeElemImpl<T> elem = queue.peek();
        if (elem == null)
            return Integer.MAX_VALUE;
        long triggerTime = elem.triggerTime;
        return Math.max((int) (triggerTime - currentTimestamp), 0);
    }
}
