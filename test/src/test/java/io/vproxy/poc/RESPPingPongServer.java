package vproxy.poc;

import vproxy.base.connection.NetEventLoop;
import vproxy.base.connection.ServerSock;
import vproxy.base.protocol.ProtocolServerConfig;
import vproxy.base.protocol.ProtocolServerHandler;
import vproxy.base.redis.RESPConfig;
import vproxy.base.redis.RESPHandler;
import vproxy.base.redis.RESPProtocolHandler;
import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.util.callback.Callback;
import vproxy.base.util.thread.VProxyThread;
import vproxy.vfd.IPPort;

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
