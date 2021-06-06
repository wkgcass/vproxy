package vproxy.base.protocol;

import vproxy.base.connection.Connection;
import vproxy.base.connection.NetEventLoop;
import vproxy.base.util.Logger;

public class SubProtocolHandlerContext<T> extends ProtocolHandlerContext<T> {
    private final ProtocolHandlerContext<?> parent;

    public SubProtocolHandlerContext(ProtocolHandlerContext<?> parent, String connectionId, Connection connection, NetEventLoop loop, ProtocolHandler<T> handler) {
        super(connectionId, connection, loop, handler);
        this.parent = parent;
    }

    @Override
    public void write(byte[] bytes) {
        if (parent != null) {
            assert Logger.lowLevelDebug("parent is specified, invoking write([..." + bytes.length + "...]) on parent");
            parent.write(bytes);
            return;
        }
        assert Logger.lowLevelDebug("parent not specified, directly write([..." + bytes.length + "...])");
        super.write(bytes);
    }
}
