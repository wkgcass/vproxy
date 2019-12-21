package vproxy.component.app;

import vproxy.component.elgroup.EventLoopGroup;
import vproxy.component.exception.AlreadyExistException;
import vproxy.component.exception.ClosedException;
import vproxy.component.proxy.ConnectorGen;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.svrgroup.ServerGroup;
import vproxy.component.svrgroup.Upstream;
import vproxy.connection.Connection;
import vproxy.connection.Connector;
import vproxy.connection.Protocol;
import vproxy.processor.Hint;
import vproxy.protocol.ProtocolHandler;
import vproxy.socks.AddressType;
import vproxy.socks.Socks5ConnectorProvider;
import vproxy.socks.Socks5ProxyContext;
import vproxy.socks.Socks5ProxyProtocolHandler;
import vproxy.util.Callback;
import vproxy.util.Tuple;
import vproxy.util.Utils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.function.Consumer;

public class Socks5Server extends TcpLB {
    // singleton in one Socks5Server object
    class Socks5ConnectorGen implements ConnectorGen<Socks5ProxyContext> {

        @Override
        public Type type() {
            return Type.handler;
        }

        @Override
        public Connector genConnector(Connection accepted, Hint hint) {
            // will not be called
            return null;
        }

        @Override
        public ProtocolHandler<Tuple<Socks5ProxyContext, Callback<Connector, IOException>>> handler() {
            return new Socks5ProxyProtocolHandler(connectorProvider);
        }
    }

    // singleton in one Socks5Server object
    class Socks5ServerConnectorProvider implements Socks5ConnectorProvider {
        @Override
        public void provide(Connection accepted, AddressType type, String address, int port, Consumer<Connector> providedCallback) {
            // check whitelist
            InetAddress remoteAddress = accepted.remote.getAddress();
            if (!securityGroup.allow(Protocol.TCP, remoteAddress, bindAddress.getPort())) {
                providedCallback.accept(null);
                return; // terminated by securityGroup
            }

            // then let's try to find a connector
            Upstream upstream = Socks5Server.super.backend;
            if (type == AddressType.domain) {
                String addrport = address + ":" + port;
                // search for a group with name same as the address:port
                for (Upstream.ServerGroupHandle gh : upstream.getServerGroupHandles()) {
                    if (gh.alias.equals(addrport)) { // matches
                        providedCallback.accept(gh.group.next(accepted.remote));
                        return;
                    }
                }
            } else {
                // we do a search regardless of the `allowNonBackend` flag
                // because connection and netflow can be recorded, and down process can be accelerated
                // and maybe more advantages in the future

                // search for the backend in all groups
                for (Upstream.ServerGroupHandle gh : upstream.getServerGroupHandles()) {
                    for (ServerGroup.ServerHandle sh : gh.group.getServerHandles()) {
                        // match address and port
                        if (Utils.ipStr(sh.server.getAddress().getAddress()).equals(address) && sh.server.getPort() == port) {
                            providedCallback.accept(sh.makeConnector());
                            return;
                        }
                    }
                }
            }
            // still not found, check whether it's allowed to request non backend ip
            if (allowNonBackend) {
                handleNonBackend(type, address, port, providedCallback);
                return;
            }
            // return null if not found
            // the lib will handle it
            providedCallback.accept(null);
        }

        private void handleNonBackend(AddressType type, String address, int port, Consumer<Connector> providedCallback) {
            Utils.directConnect(type, address, port, providedCallback);
        }
    }

    private final Socks5ConnectorGen connectorGen = new Socks5ConnectorGen();
    private final Socks5ServerConnectorProvider connectorProvider = new Socks5ServerConnectorProvider();
    public boolean allowNonBackend = false;

    public Socks5Server(String alias, EventLoopGroup acceptorGroup, EventLoopGroup workerGroup, InetSocketAddress bindAddress, Upstream backend, int timeout, int inBufferSize, int outBufferSize, SecurityGroup securityGroup) throws IOException, AlreadyExistException, ClosedException {
        super(alias, acceptorGroup, workerGroup, bindAddress, backend, timeout, inBufferSize, outBufferSize, securityGroup);
    }

    @Override
    protected ConnectorGen provideConnectorGen() {
        // create a socks5 connector gen
        return connectorGen;
    }
}
