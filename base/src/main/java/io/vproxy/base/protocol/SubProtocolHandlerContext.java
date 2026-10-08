package io.vproxy.base.protocol;

import io.vproxy.base.connection.Connection;
import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.ByteArray;

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

    @Override
    public void write(ByteArray bytes) {
        if (parent != null) parent.write(bytes);
        else super.write(bytes);
    }

    @Override
    public void closeAfterFlush() {
        if (parent != null) parent.closeAfterFlush();
        else super.closeAfterFlush();
    }

    @Override
    public boolean isClosing() {
        return parent != null ? parent.isClosing() : super.isClosing();
    }
}
