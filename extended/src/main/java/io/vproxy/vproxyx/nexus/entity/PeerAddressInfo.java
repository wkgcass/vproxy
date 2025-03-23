package io.vproxy.vproxyx.nexus.entity;

import io.vproxy.vfd.IPPort;

public record PeerAddressInfo(IPPort ipport, int localPort) {
    public IPPort target() {
        return ipport;
    }

    @Override
    public String toString() {
        if (localPort == 0) {
            return ipport.formatToIPPortString();
        }
        return ipport.formatToIPPortString() + "@" + localPort;
    }
}
