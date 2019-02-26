package net.cassite.vproxy.pool;

import net.cassite.vproxy.connection.ClientConnection;
import net.cassite.vproxy.util.Logger;

public class PoolCallback {
    private final ConnectionPool pool;

    public PoolCallback(ConnectionPool pool) {
        this.pool = pool;
    }

    public void connectionError(ClientConnection conn) {
        assert Logger.lowLevelDebug("connection error " + conn);
        pool.removeConnection(conn);
    }

    public void handshakeDone(ClientConnection conn) {
        pool.handshakeDone(conn);
    }
}
