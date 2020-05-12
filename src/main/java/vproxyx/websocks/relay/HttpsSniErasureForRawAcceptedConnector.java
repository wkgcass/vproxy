package vproxyx.websocks.relay;

import vfd.IPPort;
import vproxy.connection.ConnectableConnection;
import vproxy.connection.Connection;
import vproxy.connection.NetEventLoop;
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
