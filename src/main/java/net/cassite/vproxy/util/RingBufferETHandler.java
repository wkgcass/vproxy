package net.cassite.vproxy.util;

public interface RingBufferETHandler {
    void readableET(); // have data, edge trigger

    void writableET(); // have free space, edge trigger

    // should trigger writable when there is no data inside the buffer
    // (event if it's previously not full)
    default boolean flushAware() {
        return false; // default: no, don't trigger
    }
}
