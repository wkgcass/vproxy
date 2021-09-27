package io.vproxy.poc;

import io.vproxy.base.connection.ConnectableConnection;
import io.vproxy.base.connection.ConnectionOpts;
import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.selector.wrap.h2streamed.H2StreamedClientFDs;
import io.vproxy.base.selector.wrap.h2streamed.H2StreamedServerFDs;
import io.vproxy.base.selector.wrap.kcp.KCPFDs;
import io.vproxy.base.util.RingBuffer;
import io.vproxy.vfd.IPPort;

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
