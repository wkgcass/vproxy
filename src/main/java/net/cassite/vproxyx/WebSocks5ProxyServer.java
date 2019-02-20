package net.cassite.vproxyx;

import net.cassite.vproxy.component.elgroup.EventLoopGroup;
import net.cassite.vproxy.component.proxy.ConnectorGen;
import net.cassite.vproxy.component.proxy.Proxy;
import net.cassite.vproxy.component.proxy.ProxyNetConfig;
import net.cassite.vproxy.connection.BindServer;
import net.cassite.vproxy.connection.Connection;
import net.cassite.vproxy.connection.Connector;
import net.cassite.vproxy.protocol.ProtocolHandler;
import net.cassite.vproxy.util.Callback;
import net.cassite.vproxy.util.Tuple;
import net.cassite.vproxyx.websocks5.WebSocks5ProtocolHandler;
import net.cassite.vproxyx.websocks5.WebSocks5ProxyContext;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

@SuppressWarnings("unused")
public class WebSocks5ProxyServer {
    public static void main0(String[] args) throws Exception {
        int port = -1;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            String next = i == args.length - 1 ? null : args[i + 1];
            if (arg.equals("listen")) {
                if (next == null) {
                    throw new IllegalArgumentException("`listen` should be follow with a port argument");
                }
                try {
                    port = Integer.parseInt(next);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("port is not a number");
                }
                if (port < 1 || port > 65535) {
                    throw new IllegalArgumentException("port is not valid");
                }
            }
        }
        if (port == -1)
            throw new IllegalArgumentException("listening port is not specified, set program argument: `listen $PORT`");

        // init the listening server
        BindServer server = BindServer.create(new InetSocketAddress(InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), port));

        // init event loops
        int threads = Math.min(4, Runtime.getRuntime().availableProcessors());
        int workers = threads;
        if (threads > 3) {
            workers -= 1; // one core for acceptor if there are at least 4 processors
        }
        // initiate the acceptor event loop(s)
        EventLoopGroup acceptor = new EventLoopGroup("acceptor-group");
        acceptor.add("acceptor-loop");
        // initiate the worker event loop(s)
        EventLoopGroup worker = new EventLoopGroup("worker-group");
        for (int i = 0; i < workers; ++i) {
            worker.add("worker-loop-" + i);
        }

        // init the proxy server
        WebSocks5ProtocolHandler webSocks5ProtocolHandler = new WebSocks5ProtocolHandler();
        ConnectorGen<WebSocks5ProxyContext> connGen = new ConnectorGen<WebSocks5ProxyContext>() {
            @Override
            public Type type() {
                return Type.handler;
            }

            @Override
            public ProtocolHandler<Tuple<WebSocks5ProxyContext, Callback<Connector, IOException>>> handler() {
                return webSocks5ProtocolHandler;
            }

            @Override
            public Connector genConnector(Connection accepted) {
                return null; // will not be called because type is `handler`
            }
        };
        Proxy proxy = new Proxy(
            new ProxyNetConfig()
                .setAcceptLoop(acceptor.next())
                .setInBufferSize(16384)
                .setOutBufferSize(16384)
                .setHandleLoopProvider(worker::next)
                .setServer(server)
                .setConnGen(() -> connGen),
            s -> {
                // do nothing, won't happen
                // when terminating, user should simply kill this process and won't close server
            });

        // start the proxy server
        proxy.handle();
    }
}
