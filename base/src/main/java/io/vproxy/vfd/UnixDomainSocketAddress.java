package io.vproxy.vfd;

import java.net.InetSocketAddress;

public class UnixDomainSocketAddress extends InetSocketAddress {
    public final UDSPath path;

    public UnixDomainSocketAddress(UDSPath path) {
        super(path.hashCode());
        this.path = path;
    }

    @Override
    public String toString() {
        return path.toString();
    }
}
