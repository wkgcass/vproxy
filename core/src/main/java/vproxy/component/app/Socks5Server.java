package vproxy.component.app;

import vfd.IP;
import vfd.IPPort;
import vproxy.component.proxy.ConnectorGen;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.svrgroup.Upstream;
import vproxybase.socks.AddressType;
import vproxy.socks.Socks5ConnectorProvider;
import vproxy.socks.Socks5ProxyContext;
import vproxy.socks.Socks5ProxyProtocolHandler;
import vproxy.util.CoreUtils;
import vproxybase.component.elgroup.EventLoopGroup;
import vproxybase.component.svrgroup.ServerGroup;
import vproxybase.connection.Connection;
import vproxybase.connection.Connector;
import vproxybase.connection.Protocol;
import vproxybase.processor.Hint;
import vproxybase.protocol.ProtocolHandler;
import vproxybase.util.Callback;
import vproxybase.util.Tuple;
import vproxybase.util.exception.AlreadyExistException;
import vproxybase.util.exception.ClosedException;

import java.io.IOException;
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
            IP remoteAddress = accepted.remote.getAddress();
            if (!securityGroup.allow(Protocol.TCP, remoteAddress, bindAddress.getPort())) {
                providedCallback.accept(null);
                return; // terminated by securityGroup
            }

            // then let's try to find a connector
            Upstream upstream = Socks5Server.super.backend;
            if (type == AddressType.domain && !IP.isIpLiteral(address) /*some implementation may always send domain socks5 request even if it's plain ip*/) {
                Hint hint = new Hint(address, port);
                Connector connector = upstream.seek(accepted.remote, hint);
                providedCallback.accept(connector);
                return;
            } else {
                // we do a search regardless of the `allowNonBackend` flag
                // because connection and netflow can be recorded, and down process can be accelerated
                // and maybe more advantages in the future

                // search for the backend in all groups
                for (Upstream.ServerGroupHandle gh : upstream.getServerGroupHandles()) {
                    for (ServerGroup.ServerHandle sh : gh.group.getServerHandles()) {
                        // match address and port
                        if (sh.server.getAddress().formatToIPString().equals(address) && sh.server.getPort() == port) {
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
            CoreUtils.directConnect(type, address, port, providedCallback);
        }
    }

    private final Socks5ConnectorGen connectorGen = new Socks5ConnectorGen();
    private final Socks5ServerConnectorProvider connectorProvider = new Socks5ServerConnectorProvider();
    public boolean allowNonBackend = false;

    public Socks5Server(String alias, EventLoopGroup acceptorGroup, EventLoopGroup workerGroup, IPPort bindAddress, Upstream backend, int timeout, int inBufferSize, int outBufferSize, SecurityGroup securityGroup) throws AlreadyExistException, ClosedException {
        super(alias, acceptorGroup, workerGroup, bindAddress, backend, timeout, inBufferSize, outBufferSize, securityGroup);
    }

    @Override
    protected ConnectorGen provideConnectorGen() {
        // create a socks5 connector gen
        return connectorGen;
    }
}
