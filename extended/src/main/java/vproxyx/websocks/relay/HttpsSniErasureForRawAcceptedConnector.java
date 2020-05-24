package vproxyx.websocks.relay;

import vfd.IPPort;
import vproxybase.connection.ConnectableConnection;
import vproxybase.connection.Connection;
import vproxybase.connection.NetEventLoop;
import vproxyx.websocks.AlreadyConnectedConnector;

public class HttpsSniErasureForRawAcceptedConnector extends AlreadyConnectedConnector {
    private final String alpn;

    public HttpsSniErasureForRawAcceptedConnector(IPPort remote, ConnectableConnection conn, NetEventLoop loop, String alpn) {
        super(remote, conn, loop);
        this.alpn = alpn;
    }

    @Override
    public void beforeConnect(Connection accepted) {
        SSLHelper.replaceToSSLBuffersToAConnectionJustReceivedClientHello(accepted, alpn);
    }
}
