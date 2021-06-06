package vproxy.vlibbase.impl;

import vproxy.base.connection.NetEventLoop;
import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.util.Logger;
import vproxy.base.util.thread.VProxyThread;
import vproxy.vlibbase.ConnRef;
import vproxy.vlibbase.ConnRefPool;

import java.io.IOException;
import java.util.Optional;

public class EmptyConnRefPool implements ConnRefPool {
    private final NetEventLoop loop;

    public EmptyConnRefPool(NetEventLoop loop) {
        if (loop == null) {
            SelectorEventLoop sLoop;
            try {
                sLoop = SelectorEventLoop.open();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            sLoop.loop(r -> VProxyThread.create(r, "empty-conn-ref-pool"));
            loop = new NetEventLoop(sLoop);
        }
        this.loop = loop;
    }

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
        if (closed) {
            return;
        }
        closed = true;
        try {
            loop.getSelectorEventLoop().close();
        } catch (IOException ignore) {
        }
    }

    @Override
    public NetEventLoop getLoop() {
        return loop;
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
