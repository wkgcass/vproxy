package vproxy.vpacket.conntrack.tcp;

public interface ListenHandler {
    void readable(ListenEntry entry);
}
