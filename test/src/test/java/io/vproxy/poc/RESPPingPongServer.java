package io.vproxy.poc;

import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.base.connection.ServerSock;
import io.vproxy.base.protocol.ProtocolServerConfig;
import io.vproxy.base.protocol.ProtocolServerHandler;
import io.vproxy.base.redis.RESPConfig;
import io.vproxy.base.redis.RESPHandler;
import io.vproxy.base.redis.RESPProtocolHandler;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.callback.Callback;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.vfd.IPPort;

import java.io.IOException;
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
            ServerSock.create(new IPPort("127.0.0.1", 16309)),
            pconfig,
            new RESPProtocolHandler(rconfig, new MyRESPHandler()));

        VProxyThread.create(selectorEventLoop::loop, "resp-ping-pong-server").start();

        RedisPingPongBlockingClient.runBlock(16309, 60, false);
        selectorEventLoop.close();
    }
}

class MyRESPHandler implements RESPHandler<Void> {
    private String elementToString(Object o) {
        if (o instanceof ByteArray)
            return o.toString();
        return null;
    }

    @Override
    public Void attachment() {
        return null;
    }

    @Override
    public void handle(Object input, Void v, Callback<Object, Throwable> cb) {
        System.out.println("got input: " + input);
        if (input instanceof ByteArray) {
            String s = input.toString();
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
            String first = ls.size() >= 1 ? elementToString(ls.get(0)) : null;
            if (first != null && "ping".equalsIgnoreCase(first)) {
                if (ls.size() == 1) {
                    cb.succeeded("PONG");
                } else if (ls.size() == 2) {
                    String second = elementToString(ls.get(1));
                    if (second != null) {
                        cb.succeeded(second);
                    } else {
                        cb.failed(new Exception("unknown input " + ls));
                    }
                } else {
                    cb.failed(new Exception("unknown input " + ls));
                }
            } else {
                cb.failed(new Exception("unknown input " + ls));
            }
        }
    }
}
