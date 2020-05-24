package vproxybase.util;

import vproxybase.Config;

import java.util.PriorityQueue;

public class TimeQueue<T> {
    PriorityQueue<TimeElem<T>> queue = new PriorityQueue<>((a, b) -> (int) (a.triggerTime - b.triggerTime));

    public TimeElem<T> push(int timeout, T elem) {
        TimeElem<T> event = new TimeElem<>(Config.currentTimestamp + timeout, elem, this);
        queue.add(event);
        return event;
    }

    public T pop() {
        TimeElem<T> elem = queue.poll();
        if (elem == null)
            return null;
        return elem.elem;
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * @return time left to the nearest timeout, Integer.MAX_VALUE means no timer event
     */
    public int nextTime() {
        TimeElem<T> elem = queue.peek();
        if (elem == null)
            return Integer.MAX_VALUE;
        long triggerTime = elem.triggerTime;
        return Math.max((int) (triggerTime - Config.currentTimestamp), 0);
    }
}
