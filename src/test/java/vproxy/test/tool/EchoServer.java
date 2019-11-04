package vproxy.test.tool;

import vfd.EventSet;
import vfd.FDProvider;
import vfd.ServerSocketFD;
import vproxy.selector.SelectorEventLoop;

import java.io.IOException;
import java.net.InetSocketAddress;

public class EchoServer {
    public EchoServer(SelectorEventLoop loop, int port) throws IOException {
        ServerSocketFD server = FDProvider.get().openServerSocketFD();
        server.bind(new InetSocketAddress(port));
        loop.add(server, EventSet.read(), null, new EchoServerHandler());
    }
}
