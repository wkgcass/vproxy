package vproxy.connection;

import vfd.IPPort;
import vproxy.util.RingBuffer;

import java.io.IOException;

public class Connector {
    public final IPPort remote;

    public Connector(IPPort remote) {
        this.remote = remote;
    }

    @SuppressWarnings("RedundantThrows")
    public void beforeConnect(@SuppressWarnings("unused") Connection accepted) throws IOException {
        // do nothing
    }

    public final ConnectableConnection connect(ConnectionOpts opts, RingBuffer in, RingBuffer out) throws IOException {
        return connect(null, opts, in, out);
    }

    public ConnectableConnection connect(Connection accepted, ConnectionOpts opts, RingBuffer in, RingBuffer out) throws IOException {
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
