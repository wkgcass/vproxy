package io.vproxy.base.component.pool;

import io.vproxy.base.connection.ConnectableConnection;
import io.vproxy.base.util.Logger;

public class PoolCallback {
    private final ConnectionPool pool;

    public PoolCallback(ConnectionPool pool) {
        this.pool = pool;
    }

    public void connectionError(ConnectableConnection conn) {
        assert Logger.lowLevelDebug("connection error " + conn);
        pool.removeConnection(conn);
    }

    public void handshakeDone(ConnectableConnection conn) {
        pool.handshakeDone(conn);
    }
}
