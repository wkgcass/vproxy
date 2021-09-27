package io.vproxy.test.tool;

import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.vfd.EventSet;
import io.vproxy.vfd.FDProvider;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.ServerSocketFD;

import java.io.IOException;

public class EchoServer {
    public EchoServer(SelectorEventLoop loop, int port) throws IOException {
        ServerSocketFD server = FDProvider.get().openServerSocketFD();
        server.bind(new IPPort(port));
        loop.add(server, EventSet.read(), null, new EchoServerHandler());
    }
}
