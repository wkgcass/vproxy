package net.cassite.vproxy.poc;

import net.cassite.vproxy.selector.SelectorEventLoop;
import net.cassite.vproxy.test.tool.EchoServerHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;

// this example shows how to create a echo server with classes defined in `selector` package
public class SelectorEventLoopEchoServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        SelectorEventLoop eventLoop = createServer(18080);
        // start loop in another thread
        new Thread(eventLoop::loop, "EventLoopThread").start();

        Thread.sleep(500);
        AlphabetBlockingClient.runBlock(18080, 10, false);
        eventLoop.close();
    }

    static SelectorEventLoop createServer(int port) throws IOException {
        // create a event loop object
        SelectorEventLoop eventLoop = SelectorEventLoop.open();
        // create a server socket
        ServerSocketChannel server = ServerSocketChannel.open();
        // bind it to local address
        server.bind(new InetSocketAddress(port));
        // add it to event loop
        eventLoop.add(server, SelectionKey.OP_ACCEPT, null, new EchoServerHandler());

        return eventLoop;
    }
}

