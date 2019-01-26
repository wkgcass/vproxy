package net.cassite.vproxy.component.proxy;

import net.cassite.vproxy.connection.Connection;
import net.cassite.vproxy.connection.Connector;

public interface ConnectorGen {
    Connector genConnector(Connection accepted);
}
