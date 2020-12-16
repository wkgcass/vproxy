package vlibbase.impl;

import vlibbase.ConnRef;
import vlibbase.ConnRefPool;
import vproxybase.util.Logger;

import java.io.IOException;
import java.util.Optional;

public class EmptyConnRefPool implements ConnRefPool {
    @Override
    public int count() {
        return 0;
    }

    @Override
    public Optional<ConnRef> get() {
        return Optional.empty();
    }

    private boolean closed = false;

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public Void receiveTransferredConnection0(ConnRef conn) {
        if (closed) {
            throw new IllegalStateException("the pool is already closed");
        }
        if (!conn.isTransferring()) {
            throw new IllegalStateException("conn " + conn + " is not transferring");
        }
        if (!conn.isValidRef()) {
            throw new IllegalStateException("conn " + conn + " is not valid");
        }

        assert Logger.lowLevelDebug("directly close the connection " + conn.raw());
        conn.raw().close();

        return null;
    }
}
