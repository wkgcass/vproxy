package vproxy.poc;

import vfd.IPPort;
import vproxybase.connection.NetEventLoop;
import vproxybase.connection.ServerSock;
import vproxybase.protocol.ProtocolServerConfig;
import vproxybase.protocol.ProtocolServerHandler;
import vproxybase.redis.RESPConfig;
import vproxybase.redis.RESPProtocolHandler;
import vproxybase.redis.application.*;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.util.Callback;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static vproxybase.redis.application.RESPCommand.*;

public class RESPApplicationServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        SelectorEventLoop loop = SelectorEventLoop.open();
        NetEventLoop netEventLoop = new NetEventLoop(loop);
        ProtocolServerHandler.apply(
            netEventLoop,
            ServerSock.create(new IPPort("127.0.0.1", 16309)),
            new ProtocolServerConfig().setInBufferSize(8).setOutBufferSize(4),
            new RESPProtocolHandler(new RESPConfig().setMaxParseLen(16384),
                new RESPApplicationHandler(new RESPApplicationConfig(), new MyRESPApplication())));

        new Thread(loop::loop).start();

        RedisIncBlockingClient.runBlock(16309, 60, false);
        loop.close();
    }
}

class MyRESPApplication implements RESPApplication<RESPApplicationContext> {
    private static final List<RESPCommand> COMMANDS = Collections.singletonList(
        new RESPCommand("INCR", 1, false, F_WRITE | F_DENYOOM | F_FAST, 1, 1, 1)
    );
    private static final Map<String, String> keys = new ConcurrentHashMap<>();

    @Override
    public RESPApplicationContext context() {
        return new RESPApplicationContext();
    }

    @Override
    public List<RESPCommand> commands() {
        return COMMANDS;
    }

    @Override
    public void handle(Object o, RESPApplicationContext respApplicationContext, Callback<Object, Throwable> cb) {
        if (o instanceof List) {
            StringBuilder sb = new StringBuilder();
            for (Object e : ((List) o)) {
                sb.append(e).append(" ");
            }
            o = sb.toString().trim();
        }
        if (!(o instanceof String)) {
            cb.failed(new Exception("fail"));
            return;
        }
        String s = (String) o;
        String[] arr = s.split(" ");
        if (arr.length != 2 || !arr[0].equalsIgnoreCase("incr")) {
            cb.failed(new Exception("fail"));
            return;
        }

        String key = arr[1];
        String v = keys.getOrDefault(key, "0");
        int iv = Integer.parseInt(v) + 1;
        keys.put(key, iv + "");
        cb.succeeded(iv + "");
    }
}
