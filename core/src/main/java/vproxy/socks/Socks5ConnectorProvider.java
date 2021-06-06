package vproxy.socks;

import vproxy.base.connection.Connection;
import vproxy.base.connection.Connector;
import vproxy.base.connection.ConnectorProvider;
import vproxy.base.socks.AddressType;
import vproxy.vfd.IP;

import java.util.function.Consumer;

public interface Socks5ConnectorProvider extends ConnectorProvider {
    void provide(Connection accepted, AddressType type, String address, int port, Consumer<Connector> providedCallback);

    @Override
    default void provide(Connection accepted, String address, int port, Consumer<Connector> providedCallback) {
        if (IP.isIpv4(address)) {
            provide(accepted, AddressType.ipv4, address, port, providedCallback);
        } else if (IP.isIpv6(address)) {
            provide(accepted, AddressType.ipv6, address, port, providedCallback);
        } else {
            provide(accepted, AddressType.domain, address, port, providedCallback);
        }
    }
}
