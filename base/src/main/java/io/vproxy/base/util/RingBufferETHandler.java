package io.vproxy.base.util;

import java.io.IOException;

public interface RingBufferETHandler {
    void readableET(); // have data, edge trigger

    void writableET(); // have free space, edge trigger

    /** A fatal buffer error, independent of transport readiness or free space. */
    default void exception(RingBuffer source, IOException error) {
    }
}
