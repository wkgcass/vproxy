package io.vproxy.component.proxy;

import io.vproxy.base.connection.Connection;
import io.vproxy.base.connection.Connector;
import io.vproxy.base.processor.Hint;
import io.vproxy.base.processor.Processor;
import io.vproxy.base.protocol.ProtocolHandler;
import io.vproxy.base.util.callback.Callback;
import io.vproxy.base.util.coll.Tuple;
import io.vproxy.base.connection.Connection;
import io.vproxy.base.connection.Connector;
import io.vproxy.base.processor.Hint;
import io.vproxy.base.processor.Processor;
import io.vproxy.base.protocol.ProtocolHandler;
import io.vproxy.base.util.callback.Callback;
import vproxy.base.util.coll.Tuple;

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
