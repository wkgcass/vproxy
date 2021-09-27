package vproxy.test.tool;

import vproxy.base.selector.SelectorEventLoop;
import vproxy.vfd.EventSet;
import vproxy.vfd.FDProvider;
import vproxy.vfd.IPPort;
import vproxy.vfd.ServerSocketFD;

import java.io.IOException;

public class EchoServer {
    public EchoServer(SelectorEventLoop loop, int port) throws IOException {
        ServerSocketFD server = FDProvider.get().openServerSocketFD();
        server.bind(new IPPort(port));
        loop.add(server, EventSet.read(), null, new EchoServerHandler());
    }
}
