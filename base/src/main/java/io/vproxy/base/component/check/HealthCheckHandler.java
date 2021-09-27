package io.vproxy.base.component.check;

import io.vproxy.vfd.SockAddr;

public interface HealthCheckHandler {
    void up(SockAddr remote);

    void down(SockAddr remote, String reason);

    void upOnce(SockAddr remote, ConnectResult cost);

    void downOnce(SockAddr remote, String reason);
}
