package vproxy.base.util.time.impl;

import vproxy.base.util.time.TimeElem;

import java.util.*;

public class TimeWheel<T> {
    public static final int WHEEL_SIZE_POWER = 5;
    public static final int WHEEL_SIZE = 1 << WHEEL_SIZE_POWER;

    private final PriorityQueue<TimeElemImpl<T>>[] slots = new PriorityQueue[WHEEL_SIZE];
    /**
     * min time unit in this time wheel
     */
    public final long tickDuration;
    /**
     * the time wheel max time interval. interval = tickDuration * WHEEL_SIZE
     */
    public final long interval;
    public final long startTimestamp;
    private int tickIndex;
    private long elemNum;
    private long currentTime;

    public TimeWheel(long tickDuration, long timestamp) {
        this.tickDuration = tickDuration;
        this.interval = this.tickDuration * WHEEL_SIZE;
        this.startTimestamp = timestamp;
        this.currentTime = timestamp;
        this.tickIndex = findSlotIndex(timestamp);
        this.elemNum = 0;

        for (int i = 0; i < slots.length; i++) {
            slots[i] = new PriorityQueue<>(Comparator.comparingLong(x -> x.triggerTime));
        }
    }

    public void add(TimeElemImpl<T> elem, long timestamp) {
        if (elem.triggerTime <= timestamp) {
            slots[tickIndex].add(elem);
        } else {
            slots[findSlotIndex(elem.triggerTime)].add(elem);
        }
        elemNum++;
    }

    private int findSlotIndex(long timestamp) {
        long timeout = timestamp - startTimestamp;
        return (int) ((timeout & (interval - 1)) / tickDuration);
    }

    /**
     * return true if it can move.
     */
    public boolean tryTick(long timestamp) {
        return timestamp - currentTime >= tickDuration;
    }

    /**
     * move the tick index to point the next slot.
     */
    public Collection<TimeElemImpl<T>> tick(long timestamp) {
        if (!tryTick(timestamp)) {
            return Collections.emptyList();
        }

        int oldIndex = tickIndex;
        int nextIndex = (oldIndex + 1) & (WHEEL_SIZE - 1);
        if (!slots[oldIndex].isEmpty()) {
            slots[nextIndex].addAll(slots[oldIndex]);
            slots[oldIndex].clear();
        }
        this.tickIndex = nextIndex;
        final PriorityQueue<TimeElemImpl<T>> queue = slots[tickIndex];
        slots[tickIndex] = new PriorityQueue<>(Comparator.comparingLong(x -> x.triggerTime));

        elemNum -= queue.size();
        currentTime += tickDuration;
        return queue;
    }

    public TimeElem<T> poll() {
        var elem = slots[tickIndex].poll();
        if (elem != null) {
            elemNum--;
        }
        return elem;
    }

    public boolean isEmpty() {
        return elemNum == 0;
    }

    public long size() {
        return elemNum;
    }

    public int nextTime(long timestamp) {
        for (int i = tickIndex; i < tickIndex + WHEEL_SIZE; i++) {
            final int index = i & (WHEEL_SIZE - 1);
            if (slots[index].isEmpty()) {
                continue;
            }

            long triggerTime = slots[index].peek().triggerTime;
            int nextTime = Math.max((int) (triggerTime - timestamp), 0);
            if (nextTime == 0 && index != tickIndex){
                slots[tickIndex].add(slots[index].poll());
            }
            return nextTime;
        }
        return Integer.MAX_VALUE;
    }

    public void remove(TimeElemImpl<T> elem) {
        if (slots[findSlotIndex(elem.triggerTime)].remove(elem)) {
            elemNum--;
        }
    }
}
