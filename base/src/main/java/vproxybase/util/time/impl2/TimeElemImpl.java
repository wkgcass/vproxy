package vproxybase.util.time.impl2;

import vproxybase.util.time.TimeElem;

public class TimeElemImpl<T> implements TimeElem<T> {
    Bucket<T> bucket;

    final long timeOffset;
    private final T value;

    public TimeElemImpl(long timeOffset, T value) {
        this.timeOffset = timeOffset;
        this.value = value;
    }

    @Override
    public T get() {
        return value;
    }

    @Override
    public void removeSelf() {
        if (bucket != null) {
            bucket.remove(this);
        }
    }
}
