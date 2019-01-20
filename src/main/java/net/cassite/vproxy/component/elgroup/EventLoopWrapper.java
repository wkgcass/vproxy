package net.cassite.vproxy.component.elgroup;

import net.cassite.vproxy.component.exception.AlreadyExistException;
import net.cassite.vproxy.component.exception.ClosedException;
import net.cassite.vproxy.component.exception.NotFoundException;
import net.cassite.vproxy.connection.*;
import net.cassite.vproxy.selector.SelectorEventLoop;
import net.cassite.vproxy.util.LogType;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.ThreadSafe;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
        public Connection getConnection(SocketChannel channel) {
            return handler.getConnection(channel);
        }

        @Override
        public void removed(ServerHandlerContext ctx) {
            handler.removed(ctx);
            servers.remove(ctx.server);
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
    private final ConcurrentHashMap<Server, Object> servers = new ConcurrentHashMap<>();
    private final ConcurrentMap<Connection, Object> connections = new ConcurrentHashMap<>();
    private static final Object _VALUE_ = new Object();
    private final ConcurrentMap<String, EventLoopAttach> attaches = new ConcurrentHashMap<>();
    private Thread thread;

    public EventLoopWrapper(String alias, SelectorEventLoop selectorEventLoop) {
        super(selectorEventLoop);
        this.alias = alias;
    }

    @Override
    public void addServer(Server server, Object attachment, ServerHandler handler) throws IOException {
        super.addServer(server, attachment, new ServerHandlerWrapper(handler));
        servers.put(server, _VALUE_);
    }

    @Override
    public void addConnection(Connection connection, Object attachment, ConnectionHandler handler) throws IOException {
        super.addConnection(connection, attachment, new ConnectionHandlerWrapper(handler));
        connections.put(connection, _VALUE_);
    }

    @Override
    public void addClientConnection(ClientConnection connection, Object attachment, ClientConnectionHandler handler) throws IOException {
        super.addClientConnection(connection, attachment, new ClientConnectionHandlerWrapper(handler));
        connections.put(connection, _VALUE_);
    }

    @ThreadSafe
    public void attachResource(EventLoopAttach resource) throws AlreadyExistException, ClosedException {
        if (selectorEventLoop.isClosed()) {
            throw new ClosedException();
        }
        if (attaches.putIfAbsent(resource.id(), resource) != null) {
            throw new AlreadyExistException();
        }
    }

    @ThreadSafe
    public void detachResource(EventLoopAttach resource) throws NotFoundException {
        if (selectorEventLoop.isClosed())
            return;
        if (attaches.remove(resource.id()) == null) {
            throw new NotFoundException();
        }
    }

    private void removeResources() {
        Iterator<EventLoopAttach> ite = attaches.values().iterator();
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
    public void copyServers(Collection<? super Server> servers) {
        servers.addAll(this.servers.keySet());
    }

    public int serverCount() {
        return this.servers.size();
    }

    // this is a very expansive operation
    public void copyConnections(Collection<? super Connection> connections) {
        connections.addAll(this.connections.keySet());
    }

    public int connectionCount() {
        return this.connections.size();
    }

    // this is a very expansive operation
    public void copyResources(Collection<? super EventLoopAttach> resources) {
        resources.addAll(this.attaches.values());
    }

    public int resourceCount() {
        return this.attaches.size();
    }

    public void loop() {
        if (thread != null) {
            throw new IllegalStateException();
        }
        // no need to set thread to null in the new thread
        // the loop will exit only when selector is closed
        // and the selector will not be able to open again
        thread = new Thread(() -> {
            this.selectorEventLoop.loop();
            removeResources();
        }, "EventLoopThread:" + alias);
        thread.start();
    }
}
