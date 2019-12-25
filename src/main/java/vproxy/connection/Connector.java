package vproxy.connection;

import vproxy.util.RingBuffer;

import java.io.IOException;
import java.net.InetSocketAddress;

public class Connector {
    public final InetSocketAddress remote;

    public Connector(InetSocketAddress remote) {
        this.remote = remote;
    }

    public void beforeConnect(@SuppressWarnings("unused") Connection accepted) throws IOException {
        // do nothing
    }

    public ConnectableConnection connect(ConnectionOpts opts, RingBuffer in, RingBuffer out) throws IOException {
        ConnectableConnection conn = ConnectableConnection.create(remote, opts, in, out);
        conn.connector = this;
        return conn;
    }

    public boolean isValid() {
        return true; // it's always valid for a manually created Connector
    }

    // let user code alert that the connection failed
    public void connectionFailed() {
        // do nothing in default implementation
    }

    // provide a event loop
    public NetEventLoop loop() {
        return null; // default: do not provide
    }

    // close the connection inside the connector
    public void close() {
        // do nothing if it's a manually created connector
    }

    @Override
    public String toString() {
        return "Connector(" + remote + ")";
    }
}
