package vproxybase.util.time;

public interface TimeElem<T> {
    T get();

    /**
     * this method should always be called on the event loop
     */
    void removeSelf();
}
