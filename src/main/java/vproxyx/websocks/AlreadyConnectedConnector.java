package vproxyx.websocks;

import vproxy.connection.ConnectableConnection;
import vproxy.connection.ConnectionOpts;
import vproxy.connection.Connector;
import vproxy.connection.NetEventLoop;
import vproxy.util.RingBuffer;

import java.io.IOException;
import java.net.InetSocketAddress;

public class AlreadyConnectedConnector extends Connector {
    protected final ConnectableConnection conn;
    protected final NetEventLoop loop;

    public AlreadyConnectedConnector(InetSocketAddress remote, ConnectableConnection conn, NetEventLoop loop) {
        super(remote);
        this.conn = conn;
        this.loop = loop;
    }

    @Override
    public ConnectableConnection connect(ConnectionOpts opts, RingBuffer in, RingBuffer out) throws IOException {
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

    @Override
    public void close() {
        conn.close();
    }

    @Override
    public String toString() {
        return "AlreadyConnectedConnector(" + conn + ", " + loop + ")";
    }
}
