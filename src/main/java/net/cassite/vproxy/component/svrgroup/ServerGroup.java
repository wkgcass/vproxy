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
import net.cassite.vproxy.connection.ConnCloseHandler;
import net.cassite.vproxy.connection.Connection;
import net.cassite.vproxy.connection.Connector;
import net.cassite.vproxy.connection.NetFlowRecorder;
import net.cassite.vproxy.util.LogType;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.Utils;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

public class ServerGroup {
    private static final Object _VALUE_ = new Object(); // value for Map in ServerHandle

    public class ServerHandle implements EventLoopAttach, NetFlowRecorder, ConnCloseHandler {
        class ServerHealthCheckHandler implements HealthCheckHandler {
            @Override
            public void up(SocketAddress remote) {
                healthy = true;
                Logger.info(LogType.HEALTH_CHECK_CHANGE,
                    "server " + ServerHandle.this.alias + "(" + server + ") status changed to UP");
            }

            @Override
            public void down(SocketAddress remote) {
                healthy = false;
                Logger.info(LogType.HEALTH_CHECK_CHANGE,
                    "server " + ServerHandle.this.alias + "(" + server + ") status changed to DOWN");
            }

            @Override
            public void upOnce(SocketAddress remote) {
                // do nothing but debug log
                assert Logger.lowLevelDebug("up once for " + ServerHandle.this.alias + "(" + server + ")");
            }

            @Override
            public void downOnce(SocketAddress remote) {
                // do nothing but debug log
                assert Logger.lowLevelDebug("down once for " + ServerHandle.this.alias + "(" + server + ")");
            }
        }

        public final String alias;
        private final ServerHealthCheckHandler handler = new ServerHealthCheckHandler();
        public final InetSocketAddress server;
        public final InetAddress local;
        private int weight;
        EventLoopWrapper el;
        boolean valid = true;
        // NOTE: healthy state is public
        public boolean healthy = false; // considered to be unhealthy when firstly created
        TCPHealthCheckClient healthCheckClient;

        private final LongAdder fromRemoteBytes = new LongAdder();
        private final LongAdder toRemoteBytes = new LongAdder();

        private ConcurrentMap<Connection, Object> connMap = new ConcurrentHashMap<>();

        ServerHandle(String alias, InetSocketAddress server, InetAddress local, int initialWeight) {
            this.alias = alias;
            this.server = server;
            this.local = local;
            this.weight = initialWeight;
        }

        // --- START statistics ---
        @Override
        public void incToRemoteBytes(long bytes) {
            toRemoteBytes.add(bytes);
        }

        @Override
        public void incFromRemoteBytes(long bytes) {
            fromRemoteBytes.add(bytes);
        }

        @Override
        public long getToRemoteBytes() {
            return toRemoteBytes.longValue();
        }

        @Override
        public long getFromRemoteBytes() {
            return fromRemoteBytes.longValue();
        }
        // --- END statistics ---

        @Override
        public void onConnClose(Connection conn) {
            connMap.remove(conn);
        }

        void attachConnection(Connection conn) {
            connMap.put(conn, _VALUE_);
        }

        public int connectionCount() {
            return connMap.size();
        }

        public void copyConnections(Collection<? super Connection> c) {
            c.addAll(connMap.keySet());
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
            EventLoopWrapper w = eventLoopGroup.next();
            if (w == null) {
                assert Logger.lowLevelDebug("cannot get event loop, give up for now. we will start again when there're available event loops");
                return;
            }
            el = w;
            healthCheckClient = new TCPHealthCheckClient(el, server, local, healthCheckConfig, healthy, handler);
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
            Logger.lowLevelDebug("health check for " +
                ServerHandle.this.alias + "(" + server + ") " +
                "is started on loop " + el.alias);
        }

        @Override
        public String id() {
            return "HealthCheck(" +
                ServerGroup.this.alias + "/" +
                ServerHandle.this.alias +
                "(" + Utils.ipStr(server.getAddress().getAddress()) + ":" + server.getPort() + ")" +
                ")";
        }

        @Override
        public void onClose() {
            Logger.lowLevelDebug("event loop closed, health check for " +
                ServerHandle.this.alias + "(" + server + ") is trying to restart");
            restart(); // try to restart
        }

        // NOTE: stop() is public
        public void stop() {
            if (el == null)
                return;
            Logger.lowLevelDebug("stop health check for " + ServerHandle.this.alias + "(" + server + ")");
            try {
                el.detachResource(this);
            } catch (NotFoundException e) {
                // it's ok if it's not found
                // but it's unexpected
                // we log an error
                Logger.shouldNotHappen("the resource should be attached to the event loop");
            }
            el = null;
            valid = false; // it's invalid when stopped
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
            ArrayList<ServerHandle> ls = servers;
            for (ServerHandle handle : ls) {
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
    private final Attach attach;
    private ArrayList<ServerHandle> servers = new ArrayList<>(0);

    // START fields for WRR
    static class WRR {
        int[] seq;
        final AtomicInteger wrrCursor = new AtomicInteger(0);
        final ArrayList<ServerHandle> servers; // = servers;

        WRR(List<ServerHandle> servers) {
            this.servers = new ArrayList<>(servers);
        }
    }

    private WRR _wrr;
    // END fields for WRR

    // START fields for WLC
    static class WLC {
        final ArrayList<ServerHandle> servers;

        WLC(List<ServerHandle> servers) {
            this.servers = new ArrayList<>(servers);
        }
    }

    private WLC _wlc;
    // END fields for WLC

    public ServerGroup(String alias,
                       EventLoopGroup eventLoopGroup,
                       HealthCheckConfig healthCheckConfig,
                       Method method) throws AlreadyExistException, ClosedException /*let the caller know the error*/ {
        this.alias = alias;
        this.eventLoopGroup = eventLoopGroup;
        this.healthCheckConfig = healthCheckConfig;
        this.method = method;
        this.attach = new Attach();

        resetMethodRelatedFields();
        eventLoopGroup.attachResource(attach);
    }

    /**
     * @return null if not found any healthy
     */
    public Connector next() {
        if (method == Method.wrr) {
            return wrrNext();
        } else if (method == Method.wlc) {
            return wlcNext();
        } else {
            Logger.shouldNotHappen("unsupported method " + method);
            // use wrr instead
            return wrrNext();
        }
    }

    /*
     * WLC algorithm:
     * copied from http://kb.linuxvirtualserver.org/wiki/Weighted_Least-Connection_Scheduling
     *
     * Supposing there is a server set S = {S0, S1, ..., Sn-1},
     * W(Si) is the weight of server Si;
     * C(Si) is the current connection number of server Si;
     * CSUM = ΣC(Si) (i=0, 1, .. , n-1) is the sum of current connection numbers;
     *
     * The new connection is assigned to the server j, in which
     *   (C(Sm) / CSUM)/ W(Sm) = min { (C(Si) / CSUM) / W(Si)}  (i=0, 1, . , n-1),
     *   where W(Si) isn't zero
     * Since the CSUM is a constant in this lookup, there is no need to divide by CSUM,
     * the condition can be optimized as
     *   C(Sm) / W(Sm) = min { C(Si) / W(Si)}  (i=0, 1, . , n-1), where W(Si) isn't zero
     *
     * Since division operation eats much more CPU cycles than multiply operation, and Linux
     * does not allow float mode inside the kernel, the condition C(Sm)/W(Sm) > C(Si)/W(Si)
     * can be optimized as C(Sm)*W(Si) > C(Si)*W(Sm). The scheduling should guarantee
     * that a server will not be scheduled when its weight is zero. Therefore, the pseudo
     * code of weighted least-connection scheduling algorithm is
     *
     * for (m = 0; m < n; m++) {
     *     if (W(Sm) > 0) {
     *         for (i = m+1; i < n; i++) {
     *             if (C(Sm)*W(Si) > C(Si)*W(Sm))
     *                 m = i;
     *         }
     *         return Sm;
     *     }
     * }
     * return NULL;
     */

    private Connector wlcNext() {
        WLC wlc = _wlc;
        if (wlc.servers.isEmpty())
            return null;
        int m, n, WSm, CSm, WSi, CSi;
        ServerHandle Sm;
        m = 0;
        n = wlc.servers.size();
        // for (m = 0; m < n; ++m) {
        { // --------- START ---------
            Sm = wlc.servers.get(m);
            WSm = Sm.weight;
            CSm = Sm.connectionCount();
        } // --------- END ---------
        // if (WSm > 0) {
        for (int i = m + 1; i < n; ++i) {
            ServerHandle Si = wlc.servers.get(i);
            WSi = Si.weight;
            CSi = Si.connectionCount();
            if (CSm * WSi > CSi * WSm) {
                m = i;
                { // --------- START ---------
                    Sm = wlc.servers.get(m);
                    WSm = Sm.weight;
                    CSm = Sm.connectionCount();
                } // --------- END ---------
            }
        }
        return new SvrHandleConnector(Sm);
        // }
        // }
        // return null;
    }

    private Connector wrrNext() {
        return wrrNext(this._wrr, 0);
    }

    private Connector wrrNext(WRR wrr, int recursion) {
        if (recursion > wrr.seq.length)
            return null;
        if (wrr.seq.length == 0)
            return null; // return null if no elements

        int idx = wrr.wrrCursor.getAndIncrement();
        if (idx >= wrr.seq.length) {
            idx = idx % wrr.seq.length;
            wrr.wrrCursor.set(idx + 1);
        }
        int realIdx = wrr.seq[idx];
        ServerHandle h = wrr.servers.get(realIdx);
        if (h.healthy)
            return new SvrHandleConnector(h);
        else
            return wrrNext(wrr, recursion + 1);
    }

    private void resetMethodRelatedFields() {
        wrrReset();
        wlcReset();
    }

    private void wlcReset() {
        this._wlc = new WLC(this.servers.stream().filter(s -> s.weight > 0).collect(Collectors.toList()));
    }

    private void wrrReset() {
        WRR wrr = new WRR(this.servers.stream()
            .filter(s -> s.weight > 0) // only consider those weight > 0
            .collect(Collectors.toList()));
        if (wrr.servers.isEmpty()) {
            wrr.seq = new int[0];
        } else {
            // calculate the seq
            List<Integer> listSeq = new LinkedList<>();
            int[] weights = new int[wrr.servers.size()];
            int[] original = new int[wrr.servers.size()];
            // run calculation
            int sum = 0;
            for (int i = 0; i < wrr.servers.size(); i++) {
                ServerHandle h = wrr.servers.get(i);
                weights[i] = h.weight;
                original[i] = h.weight;
                sum += h.weight;
            }
            while (true) {
                int idx = maxIndex(weights);
                listSeq.add(idx);
                weights[idx] -= sum;
                if (calculationEnd(weights)) {
                    break;
                }
                for (int i = 0; i < weights.length; ++i) {
                    weights[i] += original[i];
                }
                sum = sum(weights); // recalculate sum
            }
            int[] seq = new int[listSeq.size()];

            // random is for this concern:
            // if you deploy multiple instances of vproxy
            // with exactly the same configuration
            // behind a (w)rr LVS or other proxy servers
            // without the `randStart`, first few connections
            // will always be made to the same server
            // which may cause some failure
            int randStart = new Random().nextInt(seq.length);
            // the random wll only run when updating config
            Iterator<Integer> ite = listSeq.iterator();
            int idx = 0;
            while (ite.hasNext()) {
                seq[(idx + randStart) % seq.length] = ite.next();
                ++idx;
            }
            wrr.seq = seq;
        }

        this._wrr = wrr;
    }

    private int sum(int[] weights) {
        int s = 0;
        for (int w : weights) {
            s += w;
        }
        return s;
    }

    private boolean calculationEnd(int[] weights) {
        for (int w : weights) {
            if (w != 0)
                return false;
        }
        return true;
    }

    private int maxIndex(int[] weights) {
        int maxIdx = 0;
        int maxVal = weights[0];
        for (int i = 1; i < weights.length; ++i) {
            if (weights[i] > maxVal) {
                maxVal = weights[i];
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    public void setMethod(Method method) {
        boolean needReload = this.method != method;
        this.method = method;
        if (needReload) {
            resetMethodRelatedFields();
        }
    }

    public Method getMethod() {
        return method;
    }

    public void setHealthCheckConfig(HealthCheckConfig healthCheckConfig) {
        assert Logger.lowLevelDebug("set new health check config " + healthCheckConfig);
        this.healthCheckConfig = healthCheckConfig;
        ArrayList<ServerHandle> ls = servers;
        for (ServerHandle handle : ls) {
            handle.restart(); // restart all health check clients
        }
    }

    public HealthCheckConfig getHealthCheckConfig() {
        return new HealthCheckConfig(healthCheckConfig);
    }

    public synchronized void add(String alias, InetSocketAddress server, InetAddress local, int weight) throws AlreadyExistException {
        ArrayList<ServerHandle> ls = servers;
        for (ServerHandle c : ls) {
            if (c.alias.equals(alias))
                throw new AlreadyExistException();
        }
        ServerHandle handle = new ServerHandle(alias, server, local, weight);
        handle.start();
        ArrayList<ServerHandle> newLs = new ArrayList<>(ls.size() + 1);
        newLs.addAll(ls);
        newLs.add(handle);
        servers = newLs;
        resetMethodRelatedFields();

        assert Logger.lowLevelDebug("server added: " + alias + "(" + server + ") to " + this.alias);
    }

    public synchronized void remove(String alias) throws NotFoundException {
        ArrayList<ServerHandle> ls = servers;
        if (ls.isEmpty())
            throw new NotFoundException();
        ArrayList<ServerHandle> newLs = new ArrayList<>(servers.size() - 1);
        boolean found = false;
        for (ServerHandle c : ls) {
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
        ArrayList<ServerHandle> ls;
        synchronized (this) {
            ls = servers;
            servers = new ArrayList<>(0);
            resetMethodRelatedFields();
        }
        for (ServerHandle s : ls) {
            s.stop();
            assert Logger.lowLevelDebug("server removed " + s.alias + " from " + this.alias);
        }
    }

    public void destroy() {
        clear();
        try {
            eventLoopGroup.detachResource(attach);
        } catch (NotFoundException e) {
            // ignore exception
        }
    }

    public List<ServerHandle> getServerHandles() {
        return new ArrayList<>(servers);
    }
}
