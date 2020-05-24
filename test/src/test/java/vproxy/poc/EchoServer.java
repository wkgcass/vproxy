package vproxy.poc;

import vproxybase.selector.SelectorEventLoop;

import java.io.IOException;

public class EchoServer {
    public static void main(String[] args) throws IOException {
        SelectorEventLoop loop = SelectorEventLoopEchoServer.createServer(19080);
        loop.loop();
    }
}
