package vproxybase.selector.wrap;

// only works on non-virtual fds
public interface WritableAware {
    void writable();
}
