package net.cassite.vproxy.component.svrgroup;

import net.cassite.vproxy.component.check.HealthCheckConfig;
import net.cassite.vproxy.component.check.HealthCheckHandler;
import net.cassite.vproxy.component.check.TCPHealthCheckClient;
import net.cassite.vproxy.component.elgroup.EventLoopAttach;
import net.cassite.vproxy.component.elgroup.EventLoopGroup;
import net.cassite.vproxy.component.elgroup.EventLoopGroupAttach;
import net.cassite.vproxy.component.elgroup.EventLoopWrapper;
import net.cassite.vproxy.component.exception.AlreadyExistException;
import net.cassite.vproxy.component.exception.ClosedException;
import net.cassite.vproxy.component.exception.NotFoundException;
import net.cassite.vproxy.selector.SelectorEventLoop;
import net.cassite.vproxy.util.LogType;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.Tuple;
import net.cassite.vproxy.util.Utils;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerGroup {
    public class ServerHealthHandle implements EventLoopAttach {
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

        public final String alias;
        private final ServerHealthCheckHandler handler = new ServerHealthCheckHandler();
        public final InetSocketAddress server;
        public final InetAddress local;
        private int weight;
        EventLoopWrapper el;
        SelectorEventLoop sel;
        // NOTE: healthy state is public
        public boolean healthy = false; // considered to be unhealthy when firstly created
        TCPHealthCheckClient healthCheckClient;

        ServerHealthHandle(String alias, InetSocketAddress server, InetAddress local, int initialWeight) {
            this.alias = alias;
            this.server = server;
            this.local = local;
            this.weight = initialWeight;
        }

        public void setWeight(int weight) {
            boolean needReload = this.weight != weight;
            this.weight = weight;
            if (needReload) {
                resetMethodRelatedFields();
            }
        }

        public int getWeight() {
            return weight;
        }

        void start() {
            if (el != null)
                return;
            restart();
        }

        void restart() {
            if (el != null)
                stop(); // event loop exists, so we stop first, then start (which makes it a `restart`)
            Tuple<EventLoopWrapper, SelectorEventLoop> tuple = eventLoopGroup.next();
            if (tuple == null) {
                assert Logger.lowLevelDebug("cannot get event loop, give up for now. we will start again when there're available event loops");
                return;
            }
            el = tuple.left;
            sel = tuple.right;
            healthCheckClient = new TCPHealthCheckClient(el, sel, server, local, healthCheckConfig, healthy, handler);
            try {
                el.attachResource(this);
            } catch (AlreadyExistException e) {
                Logger.shouldNotHappen("this resource should not have attached, this is an unrecoverable bug!!!");
            } catch (ClosedException e) {
                // the selected event loop is closed, let's restart again
                // however it's not expected to happen
                // we log an error
                Logger.shouldNotHappen("the retrieved event loop should not be closed");
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

        // NOTE: stop() is public
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
                Logger.shouldNotHappen("the resource should be attached to the event loop");
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
            return "ServerGroup:" + alias;
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
            // ignore, all handles will be done when event loop closes (which happens before event loop group closes)
        }
    }

    public final String alias;
    public final EventLoopGroup eventLoopGroup;
    private HealthCheckConfig healthCheckConfig;
    private Method method;
    private ArrayList<ServerHealthHandle> servers = new ArrayList<>(0);

    // START fields for WRR
    private final AtomicInteger wrrCursor = new AtomicInteger();
    private int[][] wrrElementRange = new int[0][/*2*/]; // (servers.size)[ (2)[minCursorValueInclusive, maxCursorValueExclusive] ]
    private int wrrRangeMax = 0;
    // END fields for WRR

    public ServerGroup(String alias,
                       EventLoopGroup eventLoopGroup,
                       HealthCheckConfig healthCheckConfig,
                       Method method) throws AlreadyExistException, ClosedException /*let the caller know the error*/ {
        this.alias = alias;
        this.eventLoopGroup = eventLoopGroup;
        this.healthCheckConfig = healthCheckConfig;
        this.method = method;

        eventLoopGroup.attachResource(new Attach());
    }

    /**
     * @return null if not found any healthy
     */
    public Connector next() {
        if (method == Method.wrr) {
            return wrrNext();
        } else {
            Logger.shouldNotHappen("unsupported method " + method);
            // use wrr instead
            return wrrNext();
        }
    }

    private Connector wrrNext() {
        ArrayList<ServerHealthHandle> ls = servers;
        int[][] range = wrrElementRange;
        return wrrNext(ls, range, 0);
    }

    private Connector wrrNext(ArrayList<ServerHealthHandle> ls, int[][] range, int recursion) {
        if (recursion > range.length) {
            return null;
        }
        ++recursion;

        if (range.length == 0) {
            return null; // no range recorded, means no servers, just return null
        }

        int idx = wrrCursor.getAndIncrement();
        // we do not check range max here,
        // if the it is recalculating,
        // and the first server is down
        // it will have no chance to retrieve any server
        // delay it after loop, which will travel through the list first
        // then it's safe to set cursor back to 0

        for (int i = 0; i < range.length; i++) {
            int[] r = range[i];
            if (r[0] <= idx && idx < r[1]) {
                // we are inside the correct range
                // (also means that this range is valid)
                // do handle
                if (i < ls.size()) {
                    // list is ok
                    ServerHealthHandle h = ls.get(i);
                    if (h.healthy) {
                        return new Connector(h.server, h.local);
                    } else {
                        // not healthy
                        // skip to the next range's minValue
                        wrrCursor.set(r[1]); // this value is valid regardless whether it's actually valid
                        // and re-run
                        return wrrNext(ls, range, recursion);
                    }
                } else {
                    // range not correspond to the list
                    // may be a concurrency
                    if (range != this.wrrElementRange) {
                        return next(); // this time, the server, method all might be changed, so let's re-run
                    }
                    // same, this is a bug
                    Logger.shouldNotHappen("maybe a bug in wrr range calculation");
                    // it should not happen, but if happens, we don't want it totally not working
                    // it might result in infinite loop if just call `next()`
                    // so we recalculate and call wrrNext(...)
                    wrrElementRange = buildRange(servers);
                    return wrrNext(servers, wrrElementRange, recursion);
                }
            }
        }
        // only reach here when idx is too large, or all servers down
        // set cursor
        if (idx >= wrrRangeMax) {
            wrrCursor.set(0);
            return wrrNext(ls, range, recursion);
        } else {
            return null; // all servers down, just return
        }
    }

    private void resetMethodRelatedFields() {
        wrrReset();
    }

    private void wrrReset() {
        this.wrrCursor.set(0);
        // it's safe to be smaller than actual range max value
        // so set this to zero in case some concurrency happen during range calculation
        this.wrrRangeMax = 0;
        this.wrrElementRange = buildRange(this.servers);
    }

    private int gcd(int a, int b) {
        if (a == b) return a;
        if (a > b) return gcd(a - b, b);
        return gcd(a, b - a);
    }

    private int gcd(List<ServerHealthHandle> ls) {
        int r = ls.get(0).weight;
        for (int i = 1; i < ls.size(); ++i) {
            r = gcd(r, ls.get(i).weight);
        }
        return r;
    }

    private int[][] buildRange(List<ServerHealthHandle> ls) {
        if (ls.isEmpty()) {
            this.wrrRangeMax = 0;
            return new int[0][/*2*/];
        }
        int gcd = gcd(ls);
        int[][] range = new int[ls.size()][2];
        int lastIdx = 0;
        for (int i = 0; i < ls.size(); i++) {
            ServerHealthHandle h = ls.get(i);
            int weightDivideGcd = h.weight / gcd;
            if (h.weight == 0) {
                // set to invalid numbers
                range[i][0] = -1;
                range[i][1] = -1;
            } else {
                range[i][0] = lastIdx;
                range[i][1] = lastIdx + weightDivideGcd; // exclusive
            }
            lastIdx += weightDivideGcd;
        }
        this.wrrRangeMax = lastIdx;
        return range;
    }

    public void setMethod(Method method) {
        boolean needReload = this.method != method;
        this.method = method;
        if (needReload) {
            resetMethodRelatedFields();
        }
    }

    public void setHealthCheckConfig(HealthCheckConfig healthCheckConfig) {
        assert Logger.lowLevelDebug("set new health check config " + healthCheckConfig);
        this.healthCheckConfig = healthCheckConfig;
        ArrayList<ServerHealthHandle> ls = servers;
        for (ServerHealthHandle handle : ls) {
            handle.restart(); // restart all health check clients
        }
    }

    public synchronized void add(String alias, InetSocketAddress server, InetAddress local, int weight) throws AlreadyExistException {
        ArrayList<ServerHealthHandle> ls = servers;
        for (ServerHealthHandle c : ls) {
            if (c.alias.equals(alias))
                throw new AlreadyExistException();
        }
        ServerHealthHandle handle = new ServerHealthHandle(alias, server, local, weight);
        handle.start();
        ArrayList<ServerHealthHandle> newLs = new ArrayList<>(ls.size() + 1);
        newLs.addAll(ls);
        newLs.add(handle);
        servers = newLs;
        resetMethodRelatedFields();

        assert Logger.lowLevelDebug("server added: " + alias + " - " + server);
    }

    public synchronized void remove(String alias) throws NotFoundException {
        ArrayList<ServerHealthHandle> ls = servers;
        if (ls.isEmpty())
            throw new NotFoundException();
        ArrayList<ServerHealthHandle> newLs = new ArrayList<>(servers.size() - 1);
        boolean found = false;
        for (ServerHealthHandle c : ls) {
            if (c.alias.equals(alias)) {
                found = true;
                c.stop();
            } else {
                newLs.add(c);
            }
        }
        if (!found)
            throw new NotFoundException();
        servers = newLs;
        resetMethodRelatedFields();

        assert Logger.lowLevelDebug("server removed " + alias + " from " + this.alias);
    }

    public void clear() {
        ArrayList<ServerHealthHandle> ls;
        synchronized (this) {
            ls = servers;
            servers = new ArrayList<>(0);
            resetMethodRelatedFields();
        }
        for (ServerHealthHandle s : ls) {
            s.stop();
            assert Logger.lowLevelDebug("server removed " + s.alias + " from " + this.alias);
        }
    }

    public List<ServerHealthHandle> getServerHealthHandles() {
        return new ArrayList<>(servers);
    }
}
