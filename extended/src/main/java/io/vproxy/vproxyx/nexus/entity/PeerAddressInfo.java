package io.vproxy.vproxyx.nexus.entity;

import io.vproxy.vfd.IPPort;

public record PeerAddressInfo(IPPort ipport) {
    public IPPort target() {
        return ipport;
    }

    @Override
    public String toString() {
        return ipport.formatToIPPortString();
    }
}
