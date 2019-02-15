package net.cassite.vproxy.component.app;

import net.cassite.vproxy.component.elgroup.EventLoopGroup;
import net.cassite.vproxy.component.exception.AlreadyExistException;
import net.cassite.vproxy.component.exception.ClosedException;
import net.cassite.vproxy.component.proxy.ConnectorGen;
import net.cassite.vproxy.component.secure.SecurityGroup;
import net.cassite.vproxy.component.svrgroup.ServerGroup;
import net.cassite.vproxy.component.svrgroup.ServerGroups;
import net.cassite.vproxy.connection.Connection;
import net.cassite.vproxy.connection.Connector;
import net.cassite.vproxy.connection.Protocol;
import net.cassite.vproxy.dns.Resolver;
import net.cassite.vproxy.protocol.ProtocolHandler;
import net.cassite.vproxy.socks.AddressType;
import net.cassite.vproxy.socks.Socks5ConnectorProvider;
import net.cassite.vproxy.socks.Socks5ProxyContext;
import net.cassite.vproxy.socks.Socks5ProxyProtocolHandler;
import net.cassite.vproxy.util.Callback;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.Tuple;
import net.cassite.vproxy.util.Utils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Socks5Server extends TcpLB {
    // singleton in one Socks5Server object
    class Socks5ConnectorGen implements ConnectorGen<Socks5ProxyContext> {

        @Override
        public Type type() {
            return Type.handler;
        }

        @Override
        public Connector genConnector(Connection accepted) {
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
            ServerGroups serverGroups = Socks5Server.super.backends;
            if (type == AddressType.domain) {
                String addrport = address + ":" + port;
                // search for a group with name same as the address:port
                for (ServerGroups.ServerGroupHandle gh : serverGroups.getServerGroups()) {
                    if (gh.alias.equals(addrport)) { // matches
                        providedCallback.accept(gh.group.next());
                        return;
                    }
                }
            } else {
                // we do a search regardless of the `allowNonBackend` flag
                // because connection and netflow can be recorded, and down process can be accelerated
                // and maybe more advantages in the future

                // search for the backend in all groups
                for (ServerGroups.ServerGroupHandle gh : serverGroups.getServerGroups()) {
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
            // we don't know which address to request the remote endpoint,
            // so we bind all
            InetAddress local;
            try {
                local = InetAddress.getByName("0.0.0.0");
            } catch (UnknownHostException e) {
                // this should not happen
                // should always succeed
                Logger.shouldNotHappen("getting 0.0.0.0 failed", e);
                providedCallback.accept(null);
                return;
            }

            if (type == AddressType.domain) { // resolve if it's domain
                Resolver.getDefault().resolve(address, new Callback<InetAddress, UnknownHostException>() {
                    @Override
                    protected void onSucceeded(InetAddress value) {
                        providedCallback.accept(new Connector(new InetSocketAddress(value, port), local));
                    }

                    @Override
                    protected void onFailed(UnknownHostException err) {
                        // resolve failed
                        assert Logger.lowLevelDebug("resolve for " + address + " failed in socks5 server" + err);
                        providedCallback.accept(null);
                    }
                });
            } else {
                if (!Utils.isIpLiteral(address)) {
                    assert Logger.lowLevelDebug("client request with an invalid ip " + address);
                    providedCallback.accept(null);
                    return;
                }
                InetAddress remote;
                try {
                    remote = InetAddress.getByName(address);
                } catch (UnknownHostException e) {
                    // should not happen when retrieving from an ip address
                    Logger.shouldNotHappen("getting " + address + " failed", e);
                    providedCallback.accept(null);
                    return;
                }
                providedCallback.accept(new Connector(new InetSocketAddress(remote, port), local));
            }
        }
    }

    private final Socks5ConnectorGen connectorGen = new Socks5ConnectorGen();
    private final Socks5ServerConnectorProvider connectorProvider = new Socks5ServerConnectorProvider();
    public boolean allowNonBackend = false;

    public Socks5Server(String alias, EventLoopGroup acceptorGroup, EventLoopGroup workerGroup, InetSocketAddress bindAddress, ServerGroups backends, int inBufferSize, int outBufferSize, SecurityGroup securityGroup) throws IOException, AlreadyExistException, ClosedException {
        super(alias, acceptorGroup, workerGroup, bindAddress, backends, inBufferSize, outBufferSize, securityGroup, 0);
    }

    @Override
    protected Supplier<ConnectorGen> provideConnectorGen() {
        // create a socks5 connector gen
        return () -> connectorGen;
    }
}
