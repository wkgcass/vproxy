package net.cassite.vproxy.component.svrgroup;

import net.cassite.vproxy.component.check.HealthCheckConfig;
import net.cassite.vproxy.component.check.HealthCheckHandler;
import net.cassite.vproxy.component.check.TCPHealthCheckClient;
import net.cassite.vproxy.component.elgroup.*;
import net.cassite.vproxy.component.exception.AlreadyExistException;
import net.cassite.vproxy.component.exception.ClosedException;
import net.cassite.vproxy.component.exception.NotFoundException;
import net.cassite.vproxy.util.LogType;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.Utils;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerGroup {
    class ServerHealthHandle implements EventLoopAttach {
        class ServerHealthCheckHandler implements HealthCheckHandler {
            @Override
            public void up(SocketAddress remote) {
                healthy = true;
                Logger.info(LogType.HEALTH_CHECK_CHANGE, "server " + server + " status changed to UP");
            }

            @Override
            public void down(SocketAddress remote) {
                healthy = false;
                Logger.info(LogType.HEALTH_CHECK_CHANGE, "server " + server + " status changed to DOWN");
            }

            @Override
            public void upOnce(SocketAddress remote) {
                // do nothing but debug log
                assert Logger.lowLevelDebug("up once for " + server);
            }

            @Override
            public void downOnce(SocketAddress remote) {
                // do nothing but debug log
                assert Logger.lowLevelDebug("down once for " + server);
            }
        }

        private final ServerHealthCheckHandler handler = new ServerHealthCheckHandler();
        final InetSocketAddress server;
        final InetAddress local;
        EventLoopWrapper el;
        boolean healthy = false; // considered to be unhealthy when firstly created
        TCPHealthCheckClient healthCheckClient;

        ServerHealthHandle(InetSocketAddress server, InetAddress local) {
            this.server = server;
            this.local = local;
        }

        void start() {
            if (el != null)
                return;
            restart();
        }

        void restart() {
            if (el != null)
                stop(); // event loop exists, so we stop first, then start (which makes it a `restart`)
            el = eventLoopGroup.next();
            if (el == null) {
                assert Logger.lowLevelDebug("cannot get event loop, give up for now. we will start again when there're available event loops");
                return;
            }
            healthCheckClient = new TCPHealthCheckClient(el, server, local, healthCheckConfig, healthy, handler);
            try {
                el.attachResource(this);
            } catch (AlreadyExistException e) {
                Logger.fatal(LogType.UNEXPECTED, "this resource should not have attached, this is an unrecoverable bug!!!");
            } catch (ClosedException e) {
                // the selected event loop is closed, let's restart again
                // however it's not expected to happen
                // we log an error
                Logger.error(LogType.UNEXPECTED, "the retrieved event loop should not be closed");
                restart();
                return;
            }
            healthCheckClient.start();
            Logger.lowLevelDebug("health check for " + server + " is started on loop " + el.alias);
        }

        @Override
        public String id() {
            return "HealthCheck(" + Utils.ipStr(server.getAddress().getAddress()) + ":" + server.getPort() + ")";
        }

        @Override
        public void onClose() {
            Logger.lowLevelDebug("event loop closed, health check for " + server + " is trying to restart");
            restart(); // try to restart
        }

        public void stop() {
            if (el == null)
                return;
            Logger.lowLevelDebug("stop health check for " + server);
            try {
                el.detachResource(this);
            } catch (NotFoundException e) {
                // it's ok if it's not found
                // but it's unexpected
                // we log an error
                Logger.error(LogType.UNEXPECTED, "the resource should be attached to the event loop");
            }
            el = null;
            if (healthCheckClient != null) {
                healthCheckClient.stop();
            }
            healthCheckClient = null;
        }

        @Override
        public String toString() {
            return id();
        }
    }

    class Attach implements EventLoopGroupAttach {
        @Override
        public String id() {
            return alias;
        }

        @Override
        public void onEventLoopAdd() {
            // event loop added to the group
            // restart all the health checks
            // it will let the health check clients separate on all event loops
            assert Logger.lowLevelDebug("onEventLoopAdd called, restart health checks");
            ArrayList<ServerHealthHandle> ls = servers;
            for (ServerHealthHandle handle : ls) {
                handle.restart();
            }
        }

        @Override
        public void onClose() {
            // ignore, all handles will be done when event loop closes
        }
    }

    public final String alias;
    public final EventLoopGroup eventLoopGroup;
    private HealthCheckConfig healthCheckConfig;
    private Method method = Method.rr;
    private ArrayList<ServerHealthHandle> servers = new ArrayList<>(0);

    private final AtomicInteger rrCursor = new AtomicInteger();

    public ServerGroup(String alias, EventLoopGroup eventLoopGroup, HealthCheckConfig healthCheckConfig) throws AlreadyExistException {
        this.alias = alias;
        this.eventLoopGroup = eventLoopGroup;
        this.healthCheckConfig = healthCheckConfig;

        try {
            eventLoopGroup.attachResource(new Attach());
        } catch (ClosedException e) {
            // ignore if event loop is closed
            // we won't added it back anyway
        }
    }

    /**
     * @return null if not found any healthy
     */
    public Connector next() {
        if (method == Method.rr) {
            return rrNext();
        } else {
            Logger.fatal(LogType.UNEXPECTED, "unsupported method " + method);
            // use rr instead
            return rrNext();
        }
    }

    private Connector rrNext() {
        ArrayList<ServerHealthHandle> ls = servers;
        return rrNext(ls, 0);
    }

    private Connector rrNext(ArrayList<ServerHealthHandle> ls, int recursion) {
        if (recursion > ls.size())
            return null; // traveled through the whole list but not found, we can return `null` now
        ++recursion;

        int idx = rrCursor.getAndIncrement();
        if (ls.size() > idx) {
            ServerHealthHandle handle = ls.get(idx);
            if (handle.healthy)
                return new Connector(handle.server, handle.local);
        } else {
            rrCursor.set(0);
        }
        return rrNext(ls, recursion);
    }

    private void resetMethodRelatedFields() {
        rrReset();
    }

    private void rrReset() {
        this.rrCursor.set(0);
    }

    public void setMethod(Method method) {
        this.method = method;
        resetMethodRelatedFields();
    }

    public void setHealthCheckConfig(HealthCheckConfig healthCheckConfig) {
        assert Logger.lowLevelDebug("set new health check config " + healthCheckConfig);
        this.healthCheckConfig = healthCheckConfig;
        ArrayList<ServerHealthHandle> ls = servers;
        for (ServerHealthHandle handle : ls) {
            handle.restart(); // restart all health check clients
        }
    }

    public synchronized void add(InetSocketAddress server, InetAddress local) throws AlreadyExistException {
        ArrayList<ServerHealthHandle> ls = servers;
        ServerHealthHandle handle = new ServerHealthHandle(server, local);
        handle.start();
        for (ServerHealthHandle c : ls) {
            if (c.server.equals(server))
                throw new AlreadyExistException();
        }
        ArrayList<ServerHealthHandle> newLs = new ArrayList<>(ls.size() + 1);
        newLs.addAll(ls);
        newLs.add(handle);
        servers = newLs;

        assert Logger.lowLevelDebug("server added " + server);
    }

    public synchronized void remove(InetSocketAddress server) throws NotFoundException {
        ArrayList<ServerHealthHandle> ls = servers;
        ArrayList<ServerHealthHandle> newLs = new ArrayList<>(servers.size() - 1);
        boolean found = false;
        for (ServerHealthHandle c : ls) {
            if (c.server.equals(server)) {
                found = true;
                c.stop();
            } else {
                newLs.add(c);
            }
        }
        if (!found)
            throw new NotFoundException();
        servers = newLs;

        assert Logger.lowLevelDebug("server removed " + server);
    }
}
