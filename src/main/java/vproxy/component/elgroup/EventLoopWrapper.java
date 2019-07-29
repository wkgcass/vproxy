package vproxy.component.elgroup;

import vproxy.component.exception.AlreadyExistException;
import vproxy.component.exception.ClosedException;
import vproxy.component.exception.NotFoundException;
import vproxy.connection.*;
import vproxy.selector.SelectorEventLoop;
import vproxy.util.*;

import java.io.IOException;
import java.nio.channels.NetworkChannel;
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
        public Tuple<RingBuffer, RingBuffer> getIOBuffers(NetworkChannel channel) {
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

    class ClientConnectionHandlerWrapper extends ConnectionHandlerWrapper implements ClientConnectionHandler {
        private final ClientConnectionHandler handler;

        ClientConnectionHandlerWrapper(ClientConnectionHandler handler) {
            super(handler);
            this.handler = handler;
        }

        @Override
        public void connected(ClientConnectionHandlerContext ctx) {
            handler.connected(ctx);
        }
    }

    public final String alias;
    private final SelectorEventLoop selectorEventLoop;
    private final ConcurrentHashSet<BindServer> servers = new ConcurrentHashSet<>();
    private final ConcurrentHashSet<Connection> connections = new ConcurrentHashSet<>();
    private final ConcurrentHashSet<EventLoopAttach> attaches = new ConcurrentHashSet<>();

    public EventLoopWrapper(String alias, SelectorEventLoop selectorEventLoop) {
        super(selectorEventLoop);
        this.alias = alias;
        this.selectorEventLoop = selectorEventLoop;
    }

    @Override
    public void addServer(BindServer server, Object attachment, ServerHandler handler) throws IOException {
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
    public void addClientConnection(ClientConnection connection, Object attachment, ClientConnectionHandler handler) throws IOException {
        connections.add(connection); // make sure the connection recorded
        try {
            super.addClientConnection(connection, attachment, new ClientConnectionHandlerWrapper(handler));
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
            throw new AlreadyExistException();
        }
    }

    @ThreadSafe
    public void detachResource(EventLoopAttach resource) throws NotFoundException {
        if (selectorEventLoop.isClosed())
            return;
        if (!attaches.remove(resource)) {
            throw new NotFoundException();
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
    public void copyServers(Collection<? super BindServer> servers) {
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
        if (getSelectorEventLoop().runningThread != null) {
            throw new IllegalStateException();
        }
        // no need to set thread to null in the new thread
        // the loop will exit only when selector is closed
        // and the selector will not be able to open again
        this.selectorEventLoop.loop(r -> new Thread(() -> {
            r.run();
            removeResources();
        }, "EventLoopThread:" + alias));
    }
}
