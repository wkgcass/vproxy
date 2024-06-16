package io.vproxy.base.component.pool;

import io.vproxy.base.connection.ConnectableConnection;
import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;

class SimpleConnectionPoolHandler implements ConnectionPoolHandler {
    private static final SimpleConnectionPoolHandler INST = new SimpleConnectionPoolHandler();

    public static SimpleConnectionPoolHandler get() {
        return INST;
    }

    public SimpleConnectionPoolHandler() {
    }

    @Override
    public ConnectableConnection provide(NetEventLoop loop) {
        return null;
    }

    @Override
    public void keepaliveReadable(ConnectableConnection conn) {
        Logger.error(LogType.INVALID_EXTERNAL_DATA, "connection pool received readable event from " + conn + ". closing ...");
        conn.close();
    }

    @Override
    public void keepalive(ConnectableConnection conn) {
        Logger.shouldNotHappen("should not reach here");
    }
}
