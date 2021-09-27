package io.vproxy.vfd.posix;

import io.vproxy.vfd.IPPort;

public interface VSocketAddress {
    IPPort toIPPort();
}
