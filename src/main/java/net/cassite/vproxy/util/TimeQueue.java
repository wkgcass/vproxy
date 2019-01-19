package net.cassite.vproxy.util;

import java.util.LinkedList;
import java.util.ListIterator;

public class TimeQueue<T> {
    LinkedList<TimeElem<T>> list = new LinkedList<>();
    private long current = 0;

    public void setCurrent(long current) {
        this.current = current;
    }

    public TimeElem<T> push(int timeout, T elem) {
        TimeElem<T> event = new TimeElem<>(current + timeout, elem, this);
        if (list.isEmpty()) {
            list.add(event);
            return event;
        }
        ListIterator<TimeElem<T>> ite = list.listIterator();
        while (ite.hasNext()) {
            TimeElem<T> e = ite.next();
            if (e.triggerTime > event.triggerTime) {
                ite.previous();
                ite.add(event);
                return event;
            }
        }
        // reach here means the event not added
        // and the timestamp is greater than any
        // add to the tail
        list.add(event);
        return event;
    }

    public T pop() {
        if (list.isEmpty())
            return null;
        return list.removeFirst().elem;
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    /**
     * @return time left to the nearest timeout, Integer.MAX_VALUE means no timer event
     */
    public int nextTime() {
        if (list.isEmpty())
            return Integer.MAX_VALUE;
        long triggerTime = list.get(0).triggerTime;
        return Math.max((int) (triggerTime - current), 0);
    }
}

