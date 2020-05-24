package vproxybase.connection;

import vproxybase.connection.Connection;
import vproxybase.connection.Connector;

import java.util.function.Consumer;

public interface ConnectorProvider {
    void provide(Connection accepted, String address, int port, Consumer<Connector> providedCallback);
}
