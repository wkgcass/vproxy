package net.cassite.vproxyx.websocks;

import net.cassite.vproxy.connection.ClientConnection;
import net.cassite.vproxy.connection.ConnectionOpts;
import net.cassite.vproxy.connection.Connector;
import net.cassite.vproxy.connection.NetEventLoop;
import net.cassite.vproxy.util.RingBuffer;

import java.io.IOException;
import java.net.InetSocketAddress;

public class AlreadyConnectedConnector extends Connector {
    private final ClientConnection conn;
    private final NetEventLoop loop;

    public AlreadyConnectedConnector(InetSocketAddress remote, ClientConnection conn, NetEventLoop loop) {
        super(remote);
        this.conn = conn;
        this.loop = loop;
    }

    @Override
    public ClientConnection connect(ConnectionOpts opts, RingBuffer in, RingBuffer out) throws IOException {
        RingBuffer oldI = conn.getInBuffer();
        RingBuffer oldO = conn.getOutBuffer();

        conn.UNSAFE_replaceBuffer(in, out);

        if (conn.getInBuffer() != oldI) {
            oldI.clean();
        }
        if (conn.getOutBuffer() != oldO) {
            oldO.clean();
        }

        return conn;

        // NOTE: the opts is ignored in this impl
    }

    @Override
    public NetEventLoop loop() {
        return loop;
    }
}
