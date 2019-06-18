package vproxyx.websocks;

import vproxy.connection.ClientConnection;
import vproxy.connection.ConnectionOpts;
import vproxy.connection.Connector;
import vproxy.connection.NetEventLoop;
import vproxy.util.RingBuffer;

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
