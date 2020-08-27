package vfd.posix;

import vfd.IPPort;
import vfd.UDSPath;

public class SocketAddressUDS implements VSocketAddress {
    public final String path;

    public SocketAddressUDS(String path) {
        this.path = path;
    }

    @Override
    public IPPort toIPPort() {
        return new UDSPath(path);
    }

    @Override
    public String toString() {
        return "SocketAddressUDS{" +
            "path='" + path + '\'' +
            '}';
    }
}
