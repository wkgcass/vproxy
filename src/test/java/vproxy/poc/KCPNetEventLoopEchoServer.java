package vproxy.poc;

import vproxy.connection.ConnectableConnection;
import vproxy.connection.ConnectionOpts;
import vproxy.connection.NetEventLoop;
import vproxy.selector.wrap.kcp.KCPFDs;
import vproxy.util.RingBuffer;

import java.io.IOException;
import java.net.InetSocketAddress;

public class KCPNetEventLoopEchoServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        NetEventLoop loop = NetEventLoopEchoServer.create(18080, KCPFDs.get());

        ConnectableConnection conn = ConnectableConnection.createUDP(new InetSocketAddress(18080),
            new ConnectionOpts(), RingBuffer.allocateDirect(1024), RingBuffer.allocateDirect(3),
            loop.getSelectorEventLoop(), KCPFDs.get());
        loop.addConnectableConnection(conn, null, new EchoClientConnectableConnectionHandler());

        Thread.sleep(10_000);
        loop.getSelectorEventLoop().close();
    }
}
