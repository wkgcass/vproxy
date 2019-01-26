package net.cassite.vproxy.example;

import net.cassite.vproxy.connection.BindServer;
import net.cassite.vproxy.connection.NetEventLoop;
import net.cassite.vproxy.protocol.ProtocolServerConfig;
import net.cassite.vproxy.protocol.ProtocolServerHandler;
import net.cassite.vproxy.redis.RESPConfig;
import net.cassite.vproxy.redis.RESPHandler;
import net.cassite.vproxy.redis.RESPProtocolHandler;
import net.cassite.vproxy.selector.SelectorEventLoop;
import net.cassite.vproxy.util.Callback;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

public class RESPPingPongServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        SelectorEventLoop selectorEventLoop = SelectorEventLoop.open();
        NetEventLoop loop = new NetEventLoop(selectorEventLoop);

        ProtocolServerConfig pconfig = new ProtocolServerConfig()
            .setInBufferSize(8)
            .setOutBufferSize(4);
        RESPConfig rconfig = new RESPConfig();

        ProtocolServerHandler.apply(loop,
            BindServer.create(new InetSocketAddress("127.0.0.1", 16379)),
            pconfig,
            new RESPProtocolHandler(rconfig, new MyRESPHandler()));

        new Thread(selectorEventLoop::loop).start();

        RedisPingPongBlockingClient.runBlock(16379, 60, false);
        selectorEventLoop.close();
    }
}

class MyRESPHandler implements RESPHandler<Void> {
    @Override
    public Void attachment() {
        return null;
    }

    @Override
    public void handle(Object input, Void v, Callback<Object, Throwable> cb) {
        System.out.println("got input: " + input);
        if (input instanceof String) {
            String s = (String) input;
            if (s.equalsIgnoreCase("ping")) {
                cb.succeeded("PONG");
                return;
            }
            if (s.startsWith("PING ") || s.startsWith("ping ")) {
                String res = s.substring("PING ".length());
                cb.succeeded(res);
                return;
            }
            cb.failed(new Exception("unknown input " + s));
        } else if (input instanceof List) {
            List ls = (List) input;
            if (ls.size() >= 1 && ls.get(0) instanceof String && "ping".equalsIgnoreCase((String) ls.get(0))) {
                if (ls.size() == 1) {
                    cb.succeeded("PONG");
                } else if (ls.size() == 2 && ls.get(1) instanceof String) {
                    cb.succeeded(ls.get(1));
                } else {
                    cb.failed(new Exception("unknown input " + ls));
                }
            } else {
                cb.failed(new Exception("unknown input " + ls));
            }
        }
    }
}
