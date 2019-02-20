package net.cassite.vproxyx.websocks5;

import net.cassite.vproxy.connection.ClientConnection;
import net.cassite.vproxy.connection.Connector;
import net.cassite.vproxy.util.RingBuffer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class AlreadyConnectedConnector extends Connector {
    private final ClientConnection conn;

    public AlreadyConnectedConnector(InetSocketAddress remote, InetAddress local, ClientConnection conn) {
        super(remote, local);
        this.conn = conn;
    }

    @Override
    public ClientConnection connect(RingBuffer in, RingBuffer out) throws IOException {
        RingBuffer oldI = conn.getInBuffer();
        RingBuffer oldO = conn.getOutBuffer();

        conn.UNSAFE_replaceBuffer(in, out);

        oldI.clean();
        oldO.clean();

        return conn;
    }
}
