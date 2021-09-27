package io.vproxy.base.connection;

public interface ConnCloseHandler {
    void onConnClose(Connection conn);
}
