package vproxy.poc;

import vfd.IPPort;
import vproxybase.connection.ConnectableConnection;
import vproxybase.connection.ConnectionOpts;
import vproxybase.connection.NetEventLoop;
import vproxybase.selector.wrap.kcp.KCPFDs;
import vproxybase.util.RingBuffer;

import java.io.IOException;

public class KCPNetEventLoopEchoServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        NetEventLoop loop = NetEventLoopEchoServer.create(18080, KCPFDs.getFast3());

        ConnectableConnection conn = ConnectableConnection.createUDP(new IPPort(18080),
            new ConnectionOpts(), RingBuffer.allocateDirect(1024), RingBuffer.allocateDirect(3),
            loop.getSelectorEventLoop(), KCPFDs.getFast3());
        loop.addConnectableConnection(conn, null, new EchoClientConnectableConnectionHandler());

        Thread.sleep(10_000);
        loop.getSelectorEventLoop().close();
    }
}
