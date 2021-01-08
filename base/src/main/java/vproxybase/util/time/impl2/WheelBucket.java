package vproxybase.util.time.impl2;

import java.util.Queue;

public class WheelBucket<T> implements Bucket<T> {
    private final TimeWheel<T> timeWheel;

    public WheelBucket(long startMs, long tickMs, int wheelSize, int layerNumber, Queue<TimeElemImpl<T>> queue) {
        this.timeWheel = new TimeWheel<T>(startMs, tickMs, wheelSize, layerNumber, queue);
    }

    @Override
    public void add(TimeElemImpl<T> elem) {
        timeWheel.add(elem);
    }

    @Override
    public void remove(TimeElemImpl<T> elem) {
        timeWheel.remove(elem);
    }

    @Override
    public void poll(long timeOffset) {
        timeWheel.poll(timeOffset);
    }

    @Override
    public boolean isEmpty() {
        return timeWheel.isEmpty();
    }
}
