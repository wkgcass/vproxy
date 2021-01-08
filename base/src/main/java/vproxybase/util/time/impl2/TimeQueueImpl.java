package vproxybase.util.time.impl2;

import vproxybase.util.time.TimeQueue;

import java.util.LinkedList;
import java.util.Queue;

public class TimeQueueImpl<T> implements TimeQueue<T> {
    private final Queue<T> resultQueue = new LinkedList<>();
    private TimeWheel<T> timeWheel;
    private Long firstTimestamp;
    private final Queue<TimeElemImpl<T>> queue = new LinkedList<>();

    public TimeElemImpl<T> add(long currentTimestamp, int timeout, T obj) {
        if (firstTimestamp == null || timeWheel == null) {
            firstTimestamp = currentTimestamp;
            timeWheel = new TimeWheel<>(0, 1, 32, 1, queue);
        }
        TimeElemImpl<T> elem = new TimeElemImpl<>(currentTimestamp - firstTimestamp + timeout, obj);
        timeWheel.add(elem);
        return elem;
    }

    public T poll() {
        if (firstTimestamp == null || timeWheel == null) {
            return null;
        }
        long current = System.currentTimeMillis();
        long offset = current - firstTimestamp;
        timeWheel.poll(offset);
        return resultQueue.poll();
    }

    @Override
    public boolean isEmpty() {
        return timeWheel.isEmpty();
    }

    @Override
    public int nextTime(long current) {
        TimeElemImpl<T> elem = queue.peek();
        if (elem == null) {
            return Integer.MAX_VALUE;
        }
        return Math.max((int) (elem.timeOffset + firstTimestamp - current), 0);
    }
}
