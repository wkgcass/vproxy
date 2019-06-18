package vproxy.component.proxy;

import vproxy.connection.Connection;
import vproxy.connection.Connector;

import java.util.function.Consumer;

public interface ConnectorProvider {
    void provide(Connection accepted, String address, int port, Consumer<Connector> providedCallback);
}
