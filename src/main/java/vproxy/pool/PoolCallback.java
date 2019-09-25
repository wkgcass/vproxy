package vproxy.pool;

import vproxy.connection.ConnectableConnection;
import vproxy.util.Logger;

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
