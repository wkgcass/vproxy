package net.cassite.vproxy.component.proxy;

import net.cassite.vproxy.connection.Connection;
import net.cassite.vproxy.connection.Connector;

import java.util.function.Consumer;

public interface ConnectorProvider {
    void provide(Connection accepted, String address, int port, Consumer<Connector> providedCallback);
}
