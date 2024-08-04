package io.vproxy.vproxyx.nexus.entity;

import io.vproxy.vfd.IPPort;

public record PeerAddressInfo(IPPort ipport, int uotPort) {
    public boolean isUOT() {
        return uotPort != 0;
    }

    public IPPort target() {
        if (isUOT()) {
            return new IPPort("127.0.0.1", uotPort);
        } else {
            return ipport;
        }
    }

    @Override
    public String toString() {
        if (isUOT()) {
            return "uot:" + uotPort + ":" + ipport.formatToIPPortString();
        }
        return ipport.formatToIPPortString();
    }
}
