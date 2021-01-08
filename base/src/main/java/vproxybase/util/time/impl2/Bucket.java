package vproxybase.util.time.impl2;

public interface Bucket<T> {
    void add(TimeElemImpl<T> elem);

    void remove(TimeElemImpl<T> elem);

    void poll(long timeOffset);

    boolean isEmpty();
}
