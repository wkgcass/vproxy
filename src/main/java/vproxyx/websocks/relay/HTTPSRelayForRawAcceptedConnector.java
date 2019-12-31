package vproxyx.websocks.relay;

import vproxy.connection.ConnectableConnection;
import vproxy.connection.Connection;
import vproxy.connection.NetEventLoop;
import vproxyx.websocks.AlreadyConnectedConnector;

import java.net.InetSocketAddress;

public class HTTPSRelayForRawAcceptedConnector extends AlreadyConnectedConnector {
    private final String alpn;

    public HTTPSRelayForRawAcceptedConnector(InetSocketAddress remote, ConnectableConnection conn, NetEventLoop loop, String alpn) {
        super(remote, conn, loop);
        this.alpn = alpn;
    }

    @Override
    public void beforeConnect(Connection accepted) {
        SSLHelper.replaceToSSLBuffersToAConnectionJustReceivedClientHello(accepted, alpn);
    }
}
