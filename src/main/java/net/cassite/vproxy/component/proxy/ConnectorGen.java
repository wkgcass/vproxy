package net.cassite.vproxy.component.proxy;

import net.cassite.vproxy.connection.Connection;
import net.cassite.vproxy.connection.Connector;
import net.cassite.vproxy.protocol.ProtocolHandler;
import net.cassite.vproxy.util.Callback;
import net.cassite.vproxy.util.Tuple;

import java.io.IOException;

public interface ConnectorGen<T> {
    enum Type {
        direct,
        handler,
    }

    default Type type() {
        return Type.direct;
    }

    Connector genConnector(Connection accepted);

    // the handler should set Tuple<T, null> to the context when init()
    // and the Callback object will be set by the Proxy lib
    default ProtocolHandler<Tuple<T, Callback<Connector, IOException>>> handler() {
        return null;
    }
}
