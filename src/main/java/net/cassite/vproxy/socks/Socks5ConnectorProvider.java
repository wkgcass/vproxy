package net.cassite.vproxy.socks;

import net.cassite.vproxy.component.proxy.ConnectorProvider;
import net.cassite.vproxy.connection.Connection;
import net.cassite.vproxy.connection.Connector;
import net.cassite.vproxy.util.Utils;

import java.util.function.Consumer;

public interface Socks5ConnectorProvider extends ConnectorProvider {
    void provide(Connection accepted, AddressType type, String address, int port, Consumer<Connector> providedCallback);

    @Override
    default void provide(Connection accepted, String address, int port, Consumer<Connector> providedCallback) {
        if (Utils.isIpv4(address)) {
            provide(accepted, AddressType.ipv4, address, port, providedCallback);
        } else if (Utils.isIpv6(address)) {
            provide(accepted, AddressType.ipv6, address, port, providedCallback);
        } else {
            provide(accepted, AddressType.domain, address, port, providedCallback);
        }
    }
}
