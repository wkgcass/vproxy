package vproxy.poc;

import vproxy.base.connection.ConnectableConnection;
import vproxy.base.connection.ConnectionOpts;
import vproxy.base.connection.NetEventLoop;
import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.selector.wrap.h2streamed.H2StreamedClientFDs;
import vproxy.base.selector.wrap.h2streamed.H2StreamedServerFDs;
import vproxy.base.selector.wrap.kcp.KCPFDs;
import vproxy.base.util.RingBuffer;
import vproxy.vfd.IPPort;

import java.io.IOException;

public class H2StreamedNetEventLoopEchoServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        SelectorEventLoop sLoop = SelectorEventLoop.open();
        KCPFDs kcpFDs = KCPFDs.getFast3();
        H2StreamedServerFDs serverFDs = new H2StreamedServerFDs(kcpFDs, sLoop, new IPPort(18080));
        NetEventLoop loop = NetEventLoopEchoServer.create(sLoop, 18080, serverFDs);

        H2StreamedClientFDs clientFDs = new H2StreamedClientFDs(kcpFDs, sLoop, new IPPort(18080));

        Thread.sleep(2_000);

        ConnectableConnection conn = ConnectableConnection.createUDP(new IPPort(18080),
            new ConnectionOpts(), RingBuffer.allocateDirect(1024), RingBuffer.allocateDirect(3),
            loop.getSelectorEventLoop(), clientFDs);
        loop.addConnectableConnection(conn, null, new EchoClientConnectableConnectionHandler());

        Thread.sleep(1000000_000);
        loop.getSelectorEventLoop().close();
    }
}
