package net.cassite.vproxy.socks;

import net.cassite.vproxy.connection.Connector;

public interface Socks5ConnectorProvider {
    Connector provide(AddressType type, String address, int port);
}
