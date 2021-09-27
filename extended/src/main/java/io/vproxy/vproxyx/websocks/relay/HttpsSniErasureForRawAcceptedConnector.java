package io.vproxy.vproxyx.websocks.relay;

import io.vproxy.base.connection.ConnectableConnection;
import io.vproxy.base.connection.Connection;
import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.vfd.IPPort;
import io.vproxy.vproxyx.websocks.AlreadyConnectedConnector;

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
