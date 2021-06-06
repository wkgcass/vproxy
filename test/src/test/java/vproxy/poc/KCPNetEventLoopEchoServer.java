package vproxy.poc;

import vproxy.base.connection.ConnectableConnection;
import vproxy.base.connection.ConnectionOpts;
import vproxy.base.connection.NetEventLoop;
import vproxy.base.selector.wrap.kcp.KCPFDs;
import vproxy.base.util.RingBuffer;
import vproxy.vfd.IPPort;

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
