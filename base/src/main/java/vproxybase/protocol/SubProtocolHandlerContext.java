package vproxybase.protocol;

import vproxybase.connection.Connection;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.util.Logger;

public class SubProtocolHandlerContext<T> extends ProtocolHandlerContext<T> {
    private final ProtocolHandlerContext<?> parent;

    public SubProtocolHandlerContext(ProtocolHandlerContext<?> parent, String connectionId, Connection connection, SelectorEventLoop loop, ProtocolHandler handler) {
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
