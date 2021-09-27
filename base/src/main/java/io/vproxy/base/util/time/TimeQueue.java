package io.vproxy.base.util.time;

import io.vproxy.base.util.time.impl.TimeQueueImpl;
import io.vproxy.base.util.time.impl.TimeQueueImpl;

public interface TimeQueue<T> {
    static <T> TimeQueue<T> create() {
        return new TimeQueueImpl<>();
    }

    TimeElem<T> add(long current, int timeout, T elem);

    /**
     * @return element of the nearest timeout event, or null if no elements. this method ignores whether the event is timed-out.
     */
    T poll();

    boolean isEmpty();

    /**
     * @param current current timestamp millis
     * @return time left to the nearest timeout, or 0 if the timeout event triggers, must not < 0, Integer.MAX_VALUE means no timer event
     */
    int nextTime(long current);
}
