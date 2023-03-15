package io.vproxy.base.connection;

public class ConnectionHandlerContext {
    public final NetEventLoop eventLoop;
    public final Connection connection;
    public final Object attachment;
    final ConnectionHandler handler;
    private boolean closedCallbackIsCalled = false;

    ConnectionHandlerContext(NetEventLoop eventLoop, Connection connection, Object attachment, ConnectionHandler handler) {
        this.eventLoop = eventLoop;
        this.connection = connection;
        this.attachment = attachment;
        this.handler = handler;
    }

    void invokeClosedCallback() {
        if (closedCallbackIsCalled) {
            return;
        }
        closedCallbackIsCalled = true;
        handler.closed(this);
    }
}
