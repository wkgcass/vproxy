package vproxy.pool;

import vproxy.connection.ConnectableConnection;
import vproxy.connection.NetEventLoop;

public interface ConnectionPoolHandler {
    // NOTE: the user code in ConnectableConnectionHandler
    // should NOT close the connection when its removed from event loop
    // AND: user code should add the connection into loop
    ConnectableConnection provide(NetEventLoop loop);

    // NOTE: the handler should consume all data in the inBuffer
    // otherwise the connection will be considered invalid
    void keepaliveReadable(ConnectableConnection conn);

    void keepalive(ConnectableConnection conn);
}
