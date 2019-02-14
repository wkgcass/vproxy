package net.cassite.vproxy.socks;

import net.cassite.vproxy.connection.Connection;
import net.cassite.vproxy.connection.Connector;

import java.util.function.Consumer;

public interface Socks5ConnectorProvider {
    void provide(Connection accepted, AddressType type, String address, int port, Consumer<Connector> providedCallback);
}
