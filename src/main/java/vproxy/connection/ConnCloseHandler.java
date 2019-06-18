package vproxy.connection;

public interface ConnCloseHandler {
    void onConnClose(Connection conn);
}
