package vproxy.component.proxy;

import vproxy.base.connection.Connection;
import vproxy.base.connection.Connector;
import vproxy.base.processor.Hint;
import vproxy.base.processor.Processor;
import vproxy.base.protocol.ProtocolHandler;
import vproxy.base.util.Callback;
import vproxy.base.util.Tuple;

import java.io.IOException;

public interface ConnectorGen<T> {
    enum Type {
        direct, // directly proxy
        handler, // do some handshake then proxy
        processor, // keep processing the connection
    }

    default Type type() {
        return Type.direct;
    }

    Connector genConnector(Connection accepted, Hint hint);

    // the handler should set Tuple<T, null> to the context when init()
    // and the Callback object will be set by the Proxy lib
    default ProtocolHandler<Tuple<T, Callback<Connector, IOException>>> handler() {
        return null;
    }

    default Processor processor() {
        return null;
    }
}
