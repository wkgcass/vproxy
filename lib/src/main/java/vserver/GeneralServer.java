package vserver;

import vfd.IP;
import vfd.IPPort;
import vproxybase.dns.Resolver;

import java.io.IOException;
import java.net.UnknownHostException;

public interface GeneralServer {
    default void listen(int port) throws IOException {
        listen(port, "0.0.0.0");
    }

    default void listenIPv6(int port) throws IOException {
        listen(port, "::");
    }

    default void listen(int port, String address) throws IOException {
        IP l3addr;
        try {
            l3addr = Resolver.getDefault().blockResolve(address);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        listen(port, l3addr);
    }

    default void listen(int port, IP addr) throws IOException {
        listen(new IPPort(addr, port));
    }

    void listen(IPPort addr) throws IOException;

    void close();
}
