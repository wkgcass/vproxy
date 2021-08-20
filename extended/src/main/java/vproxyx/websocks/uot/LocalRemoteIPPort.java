package vproxyx.websocks.uot;

import vproxy.vfd.IP;

import java.util.Objects;

public class LocalRemoteIPPort {
    public final IP localIp;
    public final IP remoteIp;
    public final int localPort;
    public final int remotePort;

    public LocalRemoteIPPort(IP localIp, IP remoteIp, int localPort, int remotePort) {
        this.localIp = localIp;
        this.remoteIp = remoteIp;
        this.localPort = localPort;
        this.remotePort = remotePort;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocalRemoteIPPort ipPort2 = (LocalRemoteIPPort) o;
        return localPort == ipPort2.localPort && remotePort == ipPort2.remotePort && localIp.equals(ipPort2.localIp) && remoteIp.equals(ipPort2.remoteIp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(localIp, remoteIp, localPort, remotePort);
    }

    @Override
    public String toString() {
        return "{" +
            localIp.formatToIPString() + ":" + localPort + "/" +
            remoteIp.formatToIPString() + ":" + remotePort + '}';
    }
}
