package vproxy.base.connection;

public interface ConnCloseHandler {
    void onConnClose(Connection conn);
}
