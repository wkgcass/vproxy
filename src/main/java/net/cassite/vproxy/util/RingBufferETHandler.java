package net.cassite.vproxy.util;

public interface RingBufferETHandler {
    void readableET(); // have data, edge trigger

    void writableET(); // have free space, edge trigger
}
