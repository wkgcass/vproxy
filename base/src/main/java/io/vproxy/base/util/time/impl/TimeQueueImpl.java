package io.vproxy.base.util.time.impl;

import io.vproxy.base.util.time.TimeElem;
import io.vproxy.base.util.time.TimeQueue;

import java.util.ArrayList;

/**
 * Event-loop-owned indexed min-heap. Keeping each element's heap index makes
 * cancellation O(log n) instead of PriorityQueue.remove(Object)'s O(n) scan.
 */
public class TimeQueueImpl<T> implements TimeQueue<T> {
    private final ArrayList<TimeElemImpl<T>> heap = new ArrayList<>();

    @Override
    public TimeElem<T> add(long currentTimestamp, int timeout, T elem) {
        TimeElemImpl<T> event = new TimeElemImpl<>(currentTimestamp + timeout, elem, this);
        event.heapIndex = heap.size();
        heap.add(event);
        siftUp(event.heapIndex);
        return event;
    }

    @Override
    public T poll() {
        TimeElemImpl<T> elem = removeAt(0);
        return elem == null ? null : elem.elem;
    }

    @Override
    public boolean isEmpty() {
        return heap.isEmpty();
    }

    @Override
    public int nextTime(long currentTimestamp) {
        if (heap.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        long triggerTime = heap.get(0).triggerTime;
        if (triggerTime <= currentTimestamp) {
            return 0;
        }
        long delta = triggerTime - currentTimestamp;
        if (delta < 0 || delta >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) delta;
    }

    void remove(TimeElemImpl<T> elem) {
        int idx = elem.heapIndex;
        if (idx < 0 || idx >= heap.size() || heap.get(idx) != elem) {
            return;
        }
        removeAt(idx);
    }

    private TimeElemImpl<T> removeAt(int idx) {
        if (idx < 0 || idx >= heap.size()) {
            return null;
        }
        int lastIdx = heap.size() - 1;
        TimeElemImpl<T> removed = heap.get(idx);
        TimeElemImpl<T> last = heap.remove(lastIdx);
        removed.heapIndex = -1;
        if (idx != lastIdx) {
            heap.set(idx, last);
            last.heapIndex = idx;
            if (!siftDown(idx)) {
                siftUp(idx);
            }
        }
        return removed;
    }

    private boolean siftDown(int idx) {
        int size = heap.size();
        int start = idx;
        TimeElemImpl<T> value = heap.get(idx);
        while (true) {
            int left = idx * 2 + 1;
            if (left >= size) break;
            int right = left + 1;
            int child = right < size && compare(heap.get(right), heap.get(left)) < 0 ? right : left;
            if (compare(value, heap.get(child)) <= 0) break;
            swap(idx, child);
            idx = child;
        }
        return idx != start;
    }

    private void siftUp(int idx) {
        while (idx > 0) {
            int parent = (idx - 1) >>> 1;
            if (compare(heap.get(parent), heap.get(idx)) <= 0) break;
            swap(parent, idx);
            idx = parent;
        }
    }

    private void swap(int a, int b) {
        TimeElemImpl<T> first = heap.get(a);
        TimeElemImpl<T> second = heap.get(b);
        heap.set(a, second);
        heap.set(b, first);
        first.heapIndex = b;
        second.heapIndex = a;
    }

    private int compare(TimeElemImpl<T> a, TimeElemImpl<T> b) {
        return Long.compare(a.triggerTime, b.triggerTime);
    }
}
