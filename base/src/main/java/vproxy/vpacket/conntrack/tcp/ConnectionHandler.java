package vproxy.vpacket.conntrack.tcp;

public interface ConnectionHandler {
    void readable(TcpEntry entry);

    void writable(TcpEntry entry);

    void destroy(TcpEntry entry);
}
