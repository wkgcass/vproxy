package net.cassite.vproxy.component.check;

import java.net.SocketAddress;

public interface HealthCheckHandler {
    void up(SocketAddress remote);

    void down(SocketAddress remote);

    void upOnce(SocketAddress remote);

    void downOnce(SocketAddress remote);
}
