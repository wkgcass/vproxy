package vproxyx.websocks.relay;

import vproxy.component.elgroup.EventLoopGroup;
import vproxy.component.proxy.ConnectorGen;
import vproxy.component.proxy.Proxy;
import vproxy.component.proxy.ProxyNetConfig;
import vproxy.connection.Connection;
import vproxy.connection.Connector;
import vproxy.connection.ServerSock;
import vproxy.processor.Hint;
import vproxy.protocol.ProtocolHandler;
import vproxy.protocol.ProtocolHandlerContext;
import vproxy.socks.AddressType;
import vproxy.util.Callback;
import vproxy.util.Logger;
import vproxy.util.Tuple;
import vproxy.util.Utils;
import vproxyx.websocks.WebSocksProxyAgentConnectorProvider;

import java.io.IOException;
import java.net.InetSocketAddress;

/*
 * For more info about this impl, please visit https://blog.cloudflare.com/how-we-built-spectrum/
 */
public class RelayBindAnyPortServer {
    private final WebSocksProxyAgentConnectorProvider connectorProvider;
    private final DomainBinder domainBinder;
    private final InetSocketAddress bindAddress;

    public RelayBindAnyPortServer(WebSocksProxyAgentConnectorProvider connectorProvider, DomainBinder domainBinder, InetSocketAddress bindAddress) {
        this.connectorProvider = connectorProvider;
        this.domainBinder = domainBinder;
        this.bindAddress = bindAddress;
    }

    public void launch(EventLoopGroup acceptor, EventLoopGroup worker) throws IOException {
        ServerSock.checkBind(bindAddress);

        ServerSock server = ServerSock.create(bindAddress, new ServerSock.BindOptions().setTransparent(true));

        Proxy proxy = new Proxy(
            new ProxyNetConfig()
                .setAcceptLoop(acceptor.next())
                .setInBufferSize(24576)
                .setOutBufferSize(24576)
                .setHandleLoopProvider(worker::next)
                .setServer(server)
                .setConnGen(new RelayBindAnyPortServerConnectorGen()),
            s -> {
                // do nothing, won't happen
                // when terminating, user should simply kill this process and won't close server
            });
        proxy.handle();
    }

    private class RelayBindAnyPortServerConnectorGen implements ConnectorGen<Void> {
        @Override
        public Type type() {
            return Type.handler;
        }

        @Override
        public Connector genConnector(Connection accepted, Hint hint) {
            return null; // will not be called
        }

        @Override
        public ProtocolHandler<Tuple<Void, Callback<Connector, IOException>>> handler() {
            return new ProtocolHandler<>() {
                private boolean done = false;

                @Override
                public void init(ProtocolHandlerContext<Tuple<Void, Callback<Connector, IOException>>> ctx) {
                    ctx.data = new Tuple<>(null, null);
                }

                @Override
                public void readable(ProtocolHandlerContext<Tuple<Void, Callback<Connector, IOException>>> ctx) {
                    var l4addr = ctx.connection.getLocal();
                    var port = l4addr.getPort();
                    var l3addr = l4addr.getAddress();
                    String hostname = domainBinder.getDomain(l3addr);
                    if (hostname == null) {
                        assert Logger.lowLevelDebug("no recorded entry for " + l3addr);
                        ctx.data.right.failed(new IOException("no available remote server connector"));
                        return;
                    } else {
                        Logger.alert("[ANY-PORT] ipMap: " + Utils.l4addrStr(l4addr) + " -> " + hostname + ":" + port);
                    }
                    connectorProvider.provide(ctx.connection, AddressType.domain, hostname, port, connector -> {
                        assert Logger.lowLevelDebug("relay-bind-any-port-server got a connector: " + connector);

                        if (connector == null) {
                            assert Logger.lowLevelDebug("no available remote server connector for now");
                            ctx.data.right.failed(new IOException("no available remote server connector"));
                            return;
                        }

                        assert Logger.lowLevelDebug("relay-bind-any-port-server is going to make a callback");
                        done = true;
                        ctx.data.right.succeeded(connector);
                    });
                }

                @Override
                public void exception(ProtocolHandlerContext<Tuple<Void, Callback<Connector, IOException>>> ctx, Throwable err) {
                    // ignore
                }

                @Override
                public void end(ProtocolHandlerContext<Tuple<Void, Callback<Connector, IOException>>> ctx) {
                    // ignore
                }

                @Override
                public boolean closeOnRemoval(ProtocolHandlerContext<Tuple<Void, Callback<Connector, IOException>>> ctx) {
                    return !done;
                }
            };
        }
    }
}
