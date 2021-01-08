package vproxybase.util.time.impl2;

import java.util.Arrays;
import java.util.List;
import java.util.Queue;

public class TimeWheel<T> {
    private final long startMs;
    private final long endMs;
    private final long tickMs;
    private final int wheelSize;
    private final long interval;
    private final List<Bucket<T>> buckets;
    private final int layerNumber;
    private TimeWheel<T> overflowWheel;
    private final Queue<TimeElemImpl<T>> queue;
    private int currentPosition = 0;

    public TimeWheel(long startMs, long tickMs, int wheelSize, int layerNumber, Queue<TimeElemImpl<T>> queue) {
        this.startMs = startMs;
        this.tickMs = tickMs;
        this.wheelSize = wheelSize;
        this.endMs = startMs + tickMs * wheelSize;
        this.queue = queue;
        this.interval = tickMs * wheelSize;
        this.buckets = Arrays.asList(new Bucket[wheelSize]);
        this.layerNumber = layerNumber;
    }

    public void add(TimeElemImpl<T> t) {
        long bucketNo = (t.timeOffset - startMs) / tickMs;
        if (bucketNo > wheelSize - 1) {
            addOverflow(t);
            return;
        }

        Bucket<T> bucket = buckets.get((int) bucketNo);
        if (bucket == null) {
            boolean isBottom = layerNumber == 1;
            if (isBottom) {
                bucket = new RawBucket<>(queue);
            } else {
                long tickMs = this.tickMs / wheelSize;
                long startMs = this.startMs + bucketNo * tickMs;
                bucket = new WheelBucket<>(startMs, tickMs, wheelSize, layerNumber - 1, queue);
            }
            buckets.set((int) bucketNo, bucket);
        }

        bucket.add(t);
    }

    private void addOverflow(TimeElemImpl<T> t) {
        if (overflowWheel == null) {
            overflowWheel = new TimeWheel<>(startMs, interval, wheelSize, layerNumber + 1, queue);
        }
        overflowWheel.add(t);
    }

    public void poll(long timeOffset) {
        if (timeOffset > endMs && overflowWheel != null) {
            overflowWheel.poll(timeOffset);
            return;
        }

        long length = (timeOffset - startMs) / tickMs;
        if (length < currentPosition) {
            return;
        }

        for (int i = currentPosition; i < length + 1; i++) {
            Bucket<T> bucket = buckets.get(i);
            if (bucket != null) {
                bucket.poll(timeOffset);
            }
        }
        currentPosition = (int) length;
    }

    public void remove(TimeElemImpl<T> obj) {
        for (Bucket<T> bucket : buckets) {
            if (bucket != null) {
                bucket.remove(obj);
            }
        }
    }

    public boolean isEmpty() {
        boolean isEmpty = true;
        for (Bucket<T> bucket : buckets) {
            if (bucket != null && !bucket.isEmpty()) {
                isEmpty = false;
            }
        }
        return isEmpty;
    }
}
