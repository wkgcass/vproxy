package net.cassite.vproxy.connection;

import net.cassite.vproxy.util.RingBuffer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class Connector {
    public final InetSocketAddress remote;
    public final InetSocketAddress local;

    public Connector(InetSocketAddress remote, InetSocketAddress local) {
        this.remote = remote;
        this.local = local;
    }

    public Connector(InetSocketAddress remote, InetAddress local) {
        this(remote, new InetSocketAddress(local, 0));
    }

    public ClientConnection connect(RingBuffer in, RingBuffer out) throws IOException {
        ClientConnection conn = ClientConnection.create(remote, local, in, out);
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

    @Override
    public String toString() {
        return "Connector(remote(" + remote + "), local(" + local + "))";
    }
}
