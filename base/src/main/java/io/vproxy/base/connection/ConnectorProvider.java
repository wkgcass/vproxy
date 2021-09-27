package vproxy.base.connection;

import java.util.function.Consumer;

public interface ConnectorProvider {
    void provide(Connection accepted, String address, int port, Consumer<Connector> providedCallback);
}
