package vproxy.test.tool;

import vfd.EventSet;
import vfd.FDProvider;
import vfd.IPPort;
import vfd.ServerSocketFD;
import vproxybase.selector.SelectorEventLoop;

import java.io.IOException;

public class EchoServer {
    public EchoServer(SelectorEventLoop loop, int port) throws IOException {
        ServerSocketFD server = FDProvider.get().openServerSocketFD();
        server.bind(new IPPort(port));
        loop.add(server, EventSet.read(), null, new EchoServerHandler());
    }
}
