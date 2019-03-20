package net.cassite.vproxy.connection;

import net.cassite.vproxy.util.RingBuffer;

import java.io.IOException;
import java.net.InetSocketAddress;

public class Connector {
    public final InetSocketAddress remote;

    public Connector(InetSocketAddress remote) {
        this.remote = remote;
    }

    public ClientConnection connect(RingBuffer in, RingBuffer out) throws IOException {
        ClientConnection conn = ClientConnection.create(remote, in, out);
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

    @Override
    public String toString() {
        return "Connector(" + remote + ")";
    }
}
