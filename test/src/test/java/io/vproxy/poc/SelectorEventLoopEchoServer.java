package vproxy.poc;

import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.util.thread.VProxyThread;
import vproxy.test.tool.EchoServerHandler;
import vproxy.vfd.EventSet;
import vproxy.vfd.FDProvider;
import vproxy.vfd.IPPort;
import vproxy.vfd.ServerSocketFD;

import java.io.IOException;

// this example shows how to create a echo server with classes defined in `selector` package
public class SelectorEventLoopEchoServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        SelectorEventLoop eventLoop = createServer(18080);
        // start loop in another thread
        VProxyThread.create(eventLoop::loop, "EventLoopThread").start();

        Thread.sleep(500);
        AlphabetBlockingClient.runBlock(18080, 10, false);
        eventLoop.close();
    }

    static SelectorEventLoop createServer(int port) throws IOException {
        // create a event loop object
        SelectorEventLoop eventLoop = SelectorEventLoop.open();
        // create a server socket
        ServerSocketFD server = FDProvider.get().openServerSocketFD();
        // bind it to local address
        server.bind(new IPPort(port));
        // add it to event loop
        eventLoop.add(server, EventSet.read(), null, new EchoServerHandler());

        return eventLoop;
    }
}

