package net.cassite.vproxy.example;

import net.cassite.vproxy.selector.SelectorEventLoop;

import java.io.IOException;

public class EchoServer {
    public static void main(String[] args) throws IOException {
        SelectorEventLoop loop = SelectorEventLoopEchoServer.createServer(19083);
        loop.loop();
    }
}
