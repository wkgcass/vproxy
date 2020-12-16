package vproxyx.websocks.relay;

import vfd.IPPort;
import vproxy.component.proxy.ConnectorGen;
import vproxy.component.proxy.Proxy;
import vproxy.component.proxy.ProxyNetConfig;
import vproxybase.socks.AddressType;
import vproxybase.component.elgroup.EventLoopGroup;
import vproxybase.connection.Connection;
import vproxybase.connection.Connector;
import vproxybase.connection.ServerSock;
import vproxybase.processor.Hint;
import vproxybase.protocol.ProtocolHandler;
import vproxybase.protocol.ProtocolHandlerContext;
import vproxybase.util.Callback;
import vproxybase.util.Logger;
import vproxybase.util.Tuple;
import vproxyx.websocks.WebSocksProxyAgentConnectorProvider;

import java.io.IOException;

/*
 * For more info about this impl, please visit https://blog.cloudflare.com/how-we-built-spectrum/
 */
public class RelayBindAnyPortServer {
    private final WebSocksProxyAgentConnectorProvider connectorProvider;
    private final DomainBinder domainBinder;
    private final IPPort bindAddress;

    public RelayBindAnyPortServer(WebSocksProxyAgentConnectorProvider connectorProvider, DomainBinder domainBinder, IPPort bindAddress) {
        this.connectorProvider = connectorProvider;
        this.domainBinder = domainBinder;
        this.bindAddress = bindAddress;
    }

    public ServerSock launch(EventLoopGroup acceptor, EventLoopGroup worker) throws IOException {
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

        return server;
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
                private boolean finished = false;
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
                        String msg = "no recorded entry for " + l3addr;
                        assert Logger.lowLevelDebug(msg);
                        finished = true;
                        ctx.data.right.failed(new IOException(msg));
                        return;
                    } else {
                        Logger.alert("[PROXY] ipMap: " + l4addr.formatToIPPortString() + " -> " + hostname + ":" + port);
                    }
                    connectorProvider.provide(ctx.connection, AddressType.domain, hostname, port, connector -> {
                        assert Logger.lowLevelDebug("relay-bind-any-port-server got a connector: " + connector + ", finished?: " + finished);

                        if (connector == null) {
                            assert Logger.lowLevelDebug("no available remote server connector for now");
                            finished = true;
                            ctx.data.right.failed(new IOException("no available remote server connector"));
                            return;
                        }
                        if (finished) {
                            connector.close();
                            return;
                        }

                        assert Logger.lowLevelDebug("relay-bind-any-port-server is going to make a callback");
                        finished = true;
                        done = true;
                        ctx.data.right.succeeded(connector);
                    });
                }

                @Override
                public void exception(ProtocolHandlerContext<Tuple<Void, Callback<Connector, IOException>>> ctx, Throwable err) {
                    if (finished) {
                        // ignore
                        return;
                    }
                    finished = true;
                    ctx.data.right.failed(new IOException(err));
                    ctx.connection.close();
                }

                @Override
                public void end(ProtocolHandlerContext<Tuple<Void, Callback<Connector, IOException>>> ctx) {
                    if (finished) {
                        // ignore
                        return;
                    }
                    finished = true;
                    ctx.data.right.failed(new IOException("frontend connection closed"));
                    ctx.connection.close();
                }

                @Override
                public boolean closeOnRemoval(ProtocolHandlerContext<Tuple<Void, Callback<Connector, IOException>>> ctx) {
                    return !done;
                }
            };
        }
    }
}
