package io.vproxy.vpacket.conntrack.udp;

public interface UdpListenHandler {
    void readable(UdpListenEntry entry);
}
