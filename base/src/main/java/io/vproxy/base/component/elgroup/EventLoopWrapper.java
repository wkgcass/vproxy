package io.vproxy.base.component.elgroup;

import io.vproxy.base.connection.*;
import vproxy.base.connection.*;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.Annotations;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.RingBuffer;
import io.vproxy.base.util.anno.ThreadSafe;
import io.vproxy.base.util.coll.ConcurrentHashSet;
import vproxy.base.util.coll.Tuple;
import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.base.util.exception.ClosedException;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.vfd.SocketFD;
import io.vproxy.vfd.VFDConfig;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

public class EventLoopWrapper extends NetEventLoop {
    class ServerHandlerWrapper implements ServerHandler {
        private final ServerHandler handler;

        ServerHandlerWrapper(ServerHandler handler) {
            this.handler = handler;
        }

        @Override
        public void acceptFail(ServerHandlerContext ctx, IOException err) {
            handler.acceptFail(ctx, err);
        }

        @Override
        public void connection(ServerHandlerContext ctx, Connection connection) {
            handler.connection(ctx, connection);
        }

        @Override
        public Tuple<RingBuffer, RingBuffer> getIOBuffers(SocketFD channel) {
            return handler.getIOBuffers(channel);
        }

        @Override
        public void removed(ServerHandlerContext ctx) {
            handler.removed(ctx);
            servers.remove(ctx.server);
        }

        @Override
        public void exception(ServerHandlerContext ctx, IOException err) {
            handler.exception(ctx, err);
        }
    }

    class ConnectionHandlerWrapper implements ConnectionHandler {
        private final ConnectionHandler handler;

        ConnectionHandlerWrapper(ConnectionHandler handler) {
            this.handler = handler;
        }

        @Override
        public void readable(ConnectionHandlerContext ctx) {
            handler.readable(ctx);
        }

        @Override
        public void writable(ConnectionHandlerContext ctx) {
            handler.writable(ctx);
        }

        @Override
        public void exception(ConnectionHandlerContext ctx, IOException err) {
            handler.exception(ctx, err);
        }

        @Override
        public void remoteClosed(ConnectionHandlerContext ctx) {
            handler.remoteClosed(ctx);
        }

        @Override
        public void closed(ConnectionHandlerContext ctx) {
            handler.closed(ctx);
        }

        @Override
        public void removed(ConnectionHandlerContext ctx) {
            handler.removed(ctx);
            connections.remove(ctx.connection);
        }
    }

    class ConnectableConnectionHandlerWrapper extends ConnectionHandlerWrapper implements ConnectableConnectionHandler {
        private final ConnectableConnectionHandler handler;

        ConnectableConnectionHandlerWrapper(ConnectableConnectionHandler handler) {
            super(handler);
            this.handler = handler;
        }

        @Override
        public void connected(ConnectableConnectionHandlerContext ctx) {
            handler.connected(ctx);
        }
    }

    public final String alias;
    private final SelectorEventLoop selectorEventLoop;
    public final Annotations annotations;
    private final ConcurrentHashSet<ServerSock> servers = new ConcurrentHashSet<>();
    private final ConcurrentHashSet<Connection> connections = new ConcurrentHashSet<>();
    private final ConcurrentHashSet<EventLoopAttach> attaches = new ConcurrentHashSet<>();

    public EventLoopWrapper(String alias, SelectorEventLoop selectorEventLoop) {
        this(alias, selectorEventLoop, new Annotations());
    }

    public EventLoopWrapper(String alias, SelectorEventLoop selectorEventLoop, Annotations annotations) {
        super(selectorEventLoop);
        this.alias = alias;
        this.selectorEventLoop = selectorEventLoop;
        this.annotations = annotations;
    }

    @Override
    public void addServer(ServerSock server, Object attachment, ServerHandler handler) throws IOException {
        servers.add(server); // make sure the server recorded
        try {
            super.addServer(server, attachment, new ServerHandlerWrapper(handler));
        } catch (IOException e) {
            servers.remove(server); // remove the recorded server if got error
            throw e;
        }
    }

    @Override
    public void addConnection(Connection connection, Object attachment, ConnectionHandler handler) throws IOException {
        connections.add(connection); // make sure the connection recorded
        try {
            super.addConnection(connection, attachment, new ConnectionHandlerWrapper(handler));
        } catch (IOException e) {
            connections.remove(connection); // remove the recorded connection if got error
            throw e;
        }
    }

    @Override
    public void addConnectableConnection(ConnectableConnection connection, Object attachment, ConnectableConnectionHandler handler) throws IOException {
        connections.add(connection); // make sure the connection recorded
        try {
            super.addConnectableConnection(connection, attachment, new ConnectableConnectionHandlerWrapper(handler));
        } catch (IOException e) {
            connections.remove(connection); // remove the recorded connection if got error
            throw e;
        }
    }

    @ThreadSafe
    public void attachResource(EventLoopAttach resource) throws AlreadyExistException, ClosedException {
        if (selectorEventLoop.isClosed()) {
            throw new ClosedException();
        }
        if (!attaches.add(resource)) {
            throw new AlreadyExistException(resource.getClass().getSimpleName(), resource.id());
        }
    }

    @ThreadSafe
    public void detachResource(EventLoopAttach resource) throws NotFoundException {
        if (selectorEventLoop.isClosed())
            return;
        if (!attaches.remove(resource)) {
            throw new NotFoundException(resource.getClass().getSimpleName(), resource.id());
        }
    }

    private void removeResources() {
        Iterator<EventLoopAttach> ite = attaches.iterator();
        while (ite.hasNext()) {
            EventLoopAttach resource = ite.next();
            ite.remove();

            try {
                resource.onClose();
            } catch (Throwable t) {
                // ignore the error, the user code should not throw
                // only log here
                Logger.error(LogType.IMPROPER_USE, "exception when calling onClose on the resource, err = ", t);
            }
        }
        // it should be cleared for now
        assert attaches.isEmpty();
    }

    // this is a very expansive operation
    public void copyServers(Collection<? super ServerSock> servers) {
        servers.addAll(this.servers);
    }

    public int serverCount() {
        return this.servers.size();
    }

    // this is a very expansive operation
    public void copyConnections(Collection<? super Connection> connections) {
        connections.addAll(this.connections);
    }

    public int connectionCount() {
        return this.connections.size();
    }

    public void loop() {
        if (VFDConfig.useFStack) {
            // f-stack programs should have only one thread and let ff_loop run the callback instead of running loop ourselves
            return;
        }
        if (getSelectorEventLoop().getRunningThread() != null) {
            throw new IllegalStateException();
        }
        // no need to set thread to null in the new thread
        // the loop will exit only when selector is closed
        // and the selector will not be able to open again
        this.selectorEventLoop.loop(r -> VProxyThread.create(() -> {
            r.run();
            removeResources();
        }, "EventLoopThread:" + alias));
    }

    @Override
    public String toString() {
        return alias + " -> annotations " + annotations.toString();
    }
}
