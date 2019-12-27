package vproxyx.websocks;

import vproxy.connection.ConnectableConnection;
import vproxy.connection.ConnectionOpts;
import vproxy.connection.NetEventLoop;
import vproxy.util.RingBuffer;

import java.net.InetSocketAddress;

public class SupplierConnector extends AlreadyConnectedConnector {
    public SupplierConnector(InetSocketAddress remote, ConnectableConnection conn, NetEventLoop loop) {
        super(remote, conn, loop);
    }

    @Override
    public ConnectableConnection connect(ConnectionOpts opts, RingBuffer in, RingBuffer out) {
        return getConnection();
    }
}
