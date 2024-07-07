package io.vproxy.vfd;

import java.io.IOException;

public interface DatagramFD extends AbstractDatagramFD<IPPort> {
    default void ensureDummyFD() throws IOException {
        bind(new IPPort("0.0.0.0", 0));
    }
}
