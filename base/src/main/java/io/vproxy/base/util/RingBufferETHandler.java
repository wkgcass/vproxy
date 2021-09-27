package vproxy.base.util;

public interface RingBufferETHandler {
    void readableET(); // have data, edge trigger

    void writableET(); // have free space, edge trigger
}
