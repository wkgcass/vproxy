package vproxy.poc;

import vfd.IPPort;
import vproxy.component.proxy.Proxy;
import vproxy.component.proxy.ProxyEventHandler;
import vproxy.component.proxy.ProxyNetConfig;
import vproxybase.connection.Connector;
import vproxybase.connection.NetEventLoop;
import vproxybase.connection.ServerSock;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.util.thread.VProxyThread;

import java.io.IOException;

// create an echo server, and create a proxy
// client requests proxy, proxy requests echo server
public class ProxyEchoServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        // start echo server
        SelectorEventLoop selectorEventLoop = SelectorEventLoopEchoServer.createServer(19080);

        // create event loop (just use the returned event loop)
        NetEventLoop netEventLoop = new NetEventLoop(selectorEventLoop);
        // create server
        ServerSock server = ServerSock.create(new IPPort(18080));
        // init config
        ProxyNetConfig config = new ProxyNetConfig()
            .setAcceptLoop(netEventLoop)
            .setConnGen((conn, hint) -> {
                // connect to localhost 19080
                return new Connector(new IPPort("127.0.0.1", 19080));
            })
            .setHandleLoopProvider(ignore -> netEventLoop) // use same event loop as the acceptor for demonstration
            .setServer(server)
            .setInBufferSize(8) // make it small to see how it acts when read buffer is full
            .setOutBufferSize(4); // make it even smaller to see how it acts when write buffer is full
        // create proxy and start
        Proxy proxy = new Proxy(config, new MyProxyEventHandler());
        proxy.handle();
        VProxyThread.create(selectorEventLoop::loop, "proxy-echo").start();

        Thread.sleep(500);
        AlphabetBlockingClient.runBlock(18080, 10, false);
        selectorEventLoop.close();
    }
}

class MyProxyEventHandler implements ProxyEventHandler {
    @Override
    public void serverRemoved(ServerSock server) {
        server.close();
    }
}
