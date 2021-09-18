package vproxy.base.util.time.impl;

import vproxy.base.util.time.TimeElem;
import vproxy.base.util.time.TimeQueue;

import java.util.*;

public class TimeQueueImpl<T> implements TimeQueue<T> {
    private static final int TIME_WHEEL_LEVEL = 4;
    private static final int MAX_TIME_WHEEL_INTERVAL = 1 << (TIME_WHEEL_LEVEL * TimeWheel.WHEEL_SIZE_POWER);

    private final PriorityQueue<TimeElemImpl<T>> queue = new PriorityQueue<>(Comparator.comparingLong(x -> x.triggerTime));

    private final ArrayList<TimeWheel<T>> timeWheels;

    private long lastTickTimestamp;

    public TimeQueueImpl() {
        this(System.currentTimeMillis());
    }

    public TimeQueueImpl(long currentTimestamp) {
        this.timeWheels = new ArrayList<>(TIME_WHEEL_LEVEL);
        for (int i = 0; i < TIME_WHEEL_LEVEL; i++) {
            this.timeWheels.add(new TimeWheel<>(1 << (i * TimeWheel.WHEEL_SIZE_POWER), currentTimestamp));
        }
        this.lastTickTimestamp = currentTimestamp;
    }

    @Override
    public TimeElem<T> add(long currentTimestamp, int timeout, T elem) {
        final TimeElemImpl<T> event = new TimeElemImpl<>(currentTimestamp + timeout, elem, this);
        addTimeElem(event, currentTimestamp);
        return event;
    }

    private void addTimeElem(TimeElemImpl<T> event, long currentTimestamp) {
        long timeout = event.triggerTime - currentTimestamp;
        if (timeout >= MAX_TIME_WHEEL_INTERVAL) {
            // long timeout task put into queue
            queue.add(event);
        } else if (timeout <= 0) {
            // already timeout task put into the lowest time wheel
            this.timeWheels.get(0).add(event, currentTimestamp);
        } else {
            var index = findTimeWheelIndex(timeout);
            this.timeWheels.get(index).add(event, currentTimestamp);
        }
    }

    @Override
    public T poll() {
        TimeElem<T> elem = timeWheels.get(0).poll();
        if (elem == null) {
            return null;
        }
        return elem.get();
    }

    @Override
    public boolean isEmpty() {
        for (TimeWheel<T> timeWheel : timeWheels) {
            if (!timeWheel.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int nextTime(long currentTimestamp) {
        tickTimeWheel(currentTimestamp);
        for (TimeWheel<T> timeWheel : timeWheels) {
            if (timeWheel.isEmpty()) {
                continue;
            }
            return timeWheel.nextTime(currentTimestamp);
        }

        TimeElemImpl<T> elem = queue.peek();
        if (elem == null) {
            return Integer.MAX_VALUE;
        }
        long triggerTime = elem.triggerTime;
        return Math.max((int) (triggerTime - currentTimestamp), 0);
    }

    private void tickTimeWheel(long currentTimestamp) {
        if (currentTimestamp <= this.lastTickTimestamp) {
            return;
        }

        for (int i = TIME_WHEEL_LEVEL - 1; i > 0; i--) {
            final var wheel = timeWheels.get(i);
            while (wheel.tryTick(currentTimestamp)) {
                final Collection<TimeElemImpl<T>> events = wheel.tick(currentTimestamp);
                for (TimeElemImpl<T> event : events) {
                    addTimeElem(event, currentTimestamp);
                }
            }
        }

        // move elements from queue to time wheels
        while (!queue.isEmpty()) {
            final TimeElemImpl<T> elem = queue.peek();
            long timeout = elem.triggerTime - currentTimestamp;
            if (timeout >= MAX_TIME_WHEEL_INTERVAL) {
                break;
            }

            addTimeElem(elem, currentTimestamp);
            queue.poll();
        }

        this.lastTickTimestamp = currentTimestamp;
    }

    public void remove(TimeElemImpl<T> elem) {
        long timeout = elem.triggerTime - this.lastTickTimestamp;
        if (timeout >= MAX_TIME_WHEEL_INTERVAL) {
            queue.remove(elem);
        } else {
            timeWheels.get(findTimeWheelIndex(timeout)).remove(elem);
        }
    }

    private static int findTimeWheelIndex(long timeout) {
        if (timeout <= 0) {
            return 0;
        }
        int bits = 63 - Long.numberOfLeadingZeros(timeout);
        return bits / TimeWheel.WHEEL_SIZE_POWER;
    }
}
