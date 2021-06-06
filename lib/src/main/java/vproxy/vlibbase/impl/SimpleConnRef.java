package vproxy.vlibbase.impl;

import vproxy.base.connection.Connection;
import vproxy.vlibbase.ConnRef;
import vproxy.vlibbase.ConnectionAware;

import java.io.IOException;

public class SimpleConnRef implements ConnRef {
    private final Connection conn;
    private boolean valid = true;
    private boolean transferring = true;

    public SimpleConnRef(Connection conn) {
        this.conn = conn;
    }

    @Override
    public boolean isValidRef() {
        return valid;
    }

    @Override
    public boolean isTransferring() {
        return transferring;
    }

    @Override
    public <T> T transferTo(ConnectionAware<T> client) throws IOException {
        if (conn.isClosed()) {
            throw new IOException("the connection " + conn + " is closed");
        }
        if (!valid) {
            throw new IOException("the ConnRef for " + conn + " is invalid");
        }
        T ret = client.receiveTransferredConnection0(this);
        valid = false;
        transferring = false;
        return ret;
    }

    @Override
    public Connection raw() {
        return conn;
    }

    @Override
    public void close() {
        if (!valid) {
            throw new IllegalStateException("the ConnRef for " + conn + " is invalid");
        }
        conn.close();
    }
}
