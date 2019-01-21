package net.cassite.vproxy.component.svrgroup;

import net.cassite.vproxy.connection.ClientConnection;
import net.cassite.vproxy.util.RingBuffer;
import net.cassite.vproxy.util.Tuple;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class Connector extends Tuple<InetSocketAddress, InetSocketAddress> {
    public final InetSocketAddress remote;
    public final InetSocketAddress local;

    public Connector(InetSocketAddress remote, InetSocketAddress local) {
        super(remote, local);
        this.remote = remote;
        this.local = local;
    }

    public Connector(InetSocketAddress remote, InetAddress local) {
        this(remote, new InetSocketAddress(local, 0));
    }

    public ClientConnection connect(RingBuffer in, RingBuffer out) throws IOException {
        return ClientConnection.create(remote, local, in, out);
    }

    @Override
    public String toString() {
        return "Connector(remote(" + remote + "), local(" + local + "))";
    }
}
