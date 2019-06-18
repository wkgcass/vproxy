package vproxy.test.tool;

import vproxy.selector.SelectorEventLoop;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;

public class EchoServer {
    public EchoServer(SelectorEventLoop loop, int port) throws IOException {
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(port));
        loop.add(server, SelectionKey.OP_ACCEPT, null, new EchoServerHandler());
    }
}
