package io.vproxy.vpacket.conntrack.tcp;

public interface TcpListenHandler {
    void readable(TcpListenEntry entry);
}
