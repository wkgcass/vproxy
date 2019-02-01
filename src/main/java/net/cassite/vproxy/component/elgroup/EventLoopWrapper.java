package net.cassite.vproxy.component.elgroup;

import net.cassite.vproxy.component.exception.AlreadyExistException;
import net.cassite.vproxy.component.exception.ClosedException;
import net.cassite.vproxy.component.exception.NotFoundException;
import net.cassite.vproxy.connection.*;
import net.cassite.vproxy.selector.SelectorEventLoop;
import net.cassite.vproxy.util.*;

import java.io.IOException;
import java.nio.channels.NetworkChannel;
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
        public Tuple<RingBuffer, RingBuffer> getIOBuffers(NetworkChannel channel) {
            return handler.getIOBuffers(channel);
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
    private final SelectorEventLoop selectorEventLoop;
    private final ConcurrentHashMap<BindServer, Object> servers = new ConcurrentHashMap<>();
    private final ConcurrentMap<Connection, Object> connections = new ConcurrentHashMap<>();
    private static final Object _VALUE_ = new Object();
    private final ConcurrentMap<String, EventLoopAttach> attaches = new ConcurrentHashMap<>();
    private Thread thread;

    public EventLoopWrapper(String alias, SelectorEventLoop selectorEventLoop) {
        super(selectorEventLoop);
        this.alias = alias;
        this.selectorEventLoop = selectorEventLoop;
    }

    @Override
    public void addServer(BindServer server, Object attachment, ServerHandler handler) throws IOException {
        servers.put(server, _VALUE_); // make sure the server recorded
        try {
            super.addServer(server, attachment, new ServerHandlerWrapper(handler));
        } catch (IOException e) {
            servers.remove(server); // remove the recorded server if got error
            throw e;
        }
    }

    @Override
    public void addConnection(Connection connection, Object attachment, ConnectionHandler handler) throws IOException {
        connections.put(connection, _VALUE_); // make sure the connection recorded
        try {
            super.addConnection(connection, attachment, new ConnectionHandlerWrapper(handler));
        } catch (IOException e) {
            connections.remove(connection); // remove the recorded connection if got error
            throw e;
        }
    }

    @Override
    public void addClientConnection(ClientConnection connection, Object attachment, ClientConnectionHandler handler) throws IOException {
        connections.put(connection, _VALUE_); // make sure the connection recorded
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
    public void copyServers(Collection<? super BindServer> servers) {
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
        this.selectorEventLoop.loop(r -> new Thread(() -> {
            r.run();
            removeResources();
        }, "EventLoopThread:" + alias));
    }
}
