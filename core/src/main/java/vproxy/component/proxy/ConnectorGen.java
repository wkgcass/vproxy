package vproxy.component.proxy;

import vproxybase.connection.Connection;
import vproxybase.connection.Connector;
import vproxybase.processor.Hint;
import vproxybase.processor.Processor;
import vproxybase.protocol.ProtocolHandler;
import vproxybase.util.Callback;
import vproxybase.util.Tuple;

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
