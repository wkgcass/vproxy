package vproxyx.websocks.relay;

import vproxy.base.connection.ConnectableConnection;
import vproxy.base.connection.Connection;
import vproxy.base.connection.NetEventLoop;
import vproxy.vfd.IPPort;
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
