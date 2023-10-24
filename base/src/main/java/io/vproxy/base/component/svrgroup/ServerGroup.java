package io.vproxy.base.component.svrgroup;

import io.vproxy.base.GlobalEvents;
import io.vproxy.base.component.check.*;
import io.vproxy.base.component.elgroup.EventLoopAttach;
import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.component.elgroup.EventLoopGroupAttach;
import io.vproxy.base.component.elgroup.EventLoopWrapper;
import io.vproxy.base.connection.ConnCloseHandler;
import io.vproxy.base.connection.Connection;
import io.vproxy.base.connection.NetFlowRecorder;
import io.vproxy.base.util.Annotations;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.coll.ConcurrentHashSet;
import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.base.util.exception.ClosedException;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.vfd.*;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ServerGroup {
    public class ServerHandle implements EventLoopAttach, NetFlowRecorder, ConnCloseHandler {
        class ServerHealthCheckHandler implements HealthCheckHandler {
            @Override
            public void up(SockAddr remote) {
                healthy = true;
                hcDownReason = null;
                Logger.info(LogType.HEALTH_CHECK_CHANGE,
                    "server " + ServerHandle.this.alias + "(" + server + ") status changed to UP");

                // remove the replaced server because this server is UP
                if (toLogicDelete != null) {
                    assert Logger.lowLevelDebug("remove old logic-delete server");
                    ServerGroup.this.remove(toLogicDelete);
                    toLogicDelete = null;
                }

                // alert event
                alertListeners(lsn -> lsn.up(ServerHandle.this));
                // alert global event
                GlobalEvents.getInstance().trigger(GlobalEvents.HEALTH_CHECK,
                    new GlobalEvents.Messages.HealthCheck(ServerHandle.this, ServerGroup.this));
            }

            @Override
            public void down(SockAddr remote, String reason) {
                healthy = false;
                Logger.warn(LogType.HEALTH_CHECK_CHANGE,
                    "server " + ServerHandle.this.alias + "(" + server + ") status changed to DOWN, reason: " + reason);

                // alert event
                alertListeners(lsn -> lsn.down(ServerHandle.this));
                // alert global event
                GlobalEvents.getInstance().trigger(GlobalEvents.HEALTH_CHECK,
                    new GlobalEvents.Messages.HealthCheck(ServerHandle.this, ServerGroup.this));
            }

            @Override
            public void upOnce(SockAddr remote, ConnectResult result) {
                assert Logger.lowLevelDebug("up once for " + ServerHandle.this.alias + "(" + server + "), cost = " + result.cost);
                hcCost.addLast(result.cost);
                if (hcCost.size() > 10) {
                    hcCost.removeFirst();
                }
            }

            @Override
            public void downOnce(SockAddr remote, String reason) {
                assert Logger.lowLevelDebug("down once for " + ServerHandle.this.alias + "(" + server + "), reason: " + reason);
                hcCost.clear();
                hcDownReason = reason;

                // the server handle is default DOWN when added
                // so there's no chance for the `down()` to be called if it's actually DOWN
                // so we think this server is DOWN when down fires

                // handle the replaced server
                // will remove logic delete flag for that
                // and will remove this server because this is DOWN
                if (toLogicDelete != null) {
                    assert Logger.lowLevelDebug("restore the logic-delete server and remove self");
                    synchronized (ServerGroup.this) {
                        // the old server is still inside the server group?
                        if (ServerGroup.this.servers.contains(toLogicDelete)) {
                            ServerHandle.this.logicDelete = true;
                            toLogicDelete.logicDelete = false;

                            // remove self
                            ServerGroup.this.remove(ServerHandle.this);
                        }
                        // else:
                        // do not remove self
                        // if the old server is not inside the group

                        // and we always remove the ref to old server
                        toLogicDelete = null;
                    }
                }
            }
        }

        public final String alias;
        private final long sid;
        public final String hostName;
        private final ServerHealthCheckHandler handler = new ServerHealthCheckHandler();
        public final IPPort server;
        private int weight;
        private ServerHandle toLogicDelete; // the server will be deleted when this server is UP, may be null
        EventLoopWrapper el;
        boolean valid = true;
        // NOTE: healthy state is public
        public boolean healthy = false; // considered to be unhealthy when firstly created
        private final LinkedList<Long> hcCost = new LinkedList<>(); // the time cost for one healthy checking result of this endpoint
        private String hcDownReason = null; // the reason for the failed health checks
        private boolean logicDelete = false; // if true, it will not be checked for dup alias nor saved to cfg file
        HealthCheckClient healthCheckClient;

        private final LongAdder fromRemoteBytes = new LongAdder();
        private final LongAdder toRemoteBytes = new LongAdder();

        private final ConcurrentHashSet<Connection> connMap = new ConcurrentHashSet<>();

        public Object data; // the data field, not used by this lib

        ServerHandle(String alias, /**/long sid/**/,
                     String hostName,
                     IPPort server,
                     int initialWeight,
                     ServerHandle toLogicDelete) {
            this.alias = alias;
            this.sid = sid;
            this.hostName = hostName;
            this.server = server;
            this.weight = initialWeight;
            this.toLogicDelete = toLogicDelete;
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

        public long getToRemoteBytes() {
            return toRemoteBytes.longValue();
        }

        public long getFromRemoteBytes() {
            return fromRemoteBytes.longValue();
        }
        // --- END statistics ---

        @Override
        public void onConnClose(Connection conn) {
            connMap.remove(conn);
        }

        void attachConnection(Connection conn) {
            connMap.add(conn);
        }

        public int connectionCount() {
            return connMap.size();
        }

        public void copyConnections(Collection<? super Connection> c) {
            c.addAll(connMap);
        }

        public boolean isLogicDelete() {
            return logicDelete;
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

        public long getHcCost() {
            return (long) hcCost.stream().mapToLong(l -> l).average().orElse(-1);
        }

        public String getHcDownReason() {
            return hcDownReason;
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
            healthCheckClient = new HealthCheckClient(el, server, healthCheckConfig, annotatedHcConfig, healthy, handler);
            try {
                el.attachResource(this);
            } catch (AlreadyExistException e) {
                Logger.shouldNotHappen("this resource should not have attached, this is an unrecoverable bug!!!");
                return;
            } catch (ClosedException e) {
                // the selected event loop is closed, let's restart again
                // however it's not expected to happen
                // we log an error
                Logger.shouldNotHappen("the retrieved event loop should not be closed");
                restart();
                return;
            }
            healthCheckClient.start();
            assert Logger.lowLevelDebug("health check for " +
                ServerHandle.this.alias + "(" + server + ") " +
                "is started on loop " + el.alias);

            // alert event
            alertListeners(lsn -> lsn.start(this));
        }

        @Override
        public String id() {
            return "HealthCheck(" +
                ServerGroup.this.alias + "/" + ServerHandle.this.alias + "(" + ServerHandle.this.sid + ")";
        }

        @Override
        public void onClose() {
            assert Logger.lowLevelDebug("event loop closed, health check for " +
                ServerHandle.this.alias + "(" + server + ") is trying to restart");
            restart(); // try to restart
        }

        void stop() {
            if (el == null)
                return;
            assert Logger.lowLevelDebug("stop health check for " + ServerHandle.this.alias + "(" + server + ")");
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

            // alert event
            alertListeners(lsn -> lsn.stop(this));
        }

        private void alertListeners(Consumer<ServerListener> code) {
            for (ServerListener lsn : serverListeners) {
                code.accept(lsn);
            }
        }

        public SvrHandleConnector makeConnector() {
            return new SvrHandleConnector(this);
        }

        @Override
        public String toString() {
            /*
             * e.g. with host
             * google -> host google.com connect-to 216.58.197.238:443 weight 10 currently UP
             * or without host
             * google -> connect-to 216.58.197.238:443 weight 10 currently UP
             * or for logic deleted: add * before alias
             * *google -> host google.com connect-to 216.58.197.238:443 weight 10 currently UP
             */
            var h = this;
            return (h.isLogicDelete() ? "*" : "") + h.alias + " ->"
                + (h.hostName == null ? "" : " host " + h.hostName /* now connected to */)
                + " connect-to " + h.server.formatToIPPortString()
                + " weight " + h.getWeight()
                + " currently " + (h.healthy ? "UP" : "DOWN")
                + " cost " + h.getHcCost()
                + " down-reason " + h.getHcDownReason();
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
    private final AnnotatedHcConfig annotatedHcConfig = new AnnotatedHcConfig();
    private Method method;
    private final Attach attach;
    private ArrayList<ServerHandle> servers = new ArrayList<>(0);
    private final CopyOnWriteArraySet<ServerListener> serverListeners = new CopyOnWriteArraySet<>();
    private Annotations annotations = new Annotations();

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
    private WRR _wrrIPv4;
    private WRR _wrrIPv6;
    // END fields for WRR

    // START fields for WLC
    static class WLC {
        final ArrayList<ServerHandle> servers;

        WLC(List<ServerHandle> servers) {
            this.servers = new ArrayList<>(servers);
        }
    }

    private WLC _wlc;
    private WLC _wlcIPv4;
    private WLC _wlcIPv6;
    // END fields for WLC

    // START fields for SOURCE
    static class SOURCE {
        final int[] seq;
        final ArrayList<ServerHandle> servers;

        SOURCE(int[] seq, ArrayList<ServerHandle> servers) {
            this.seq = seq;
            this.servers = servers;
        }

        // sdbm
        int hash(byte[] bytes) {
            int hash = 0;
            for (byte aByte : bytes) {
                hash = (aByte) + (hash << 6) + (hash << 16) - hash;
            }
            hash = Math.abs(hash);
            if (hash < 0) { // Math.abs(-Integer.MAX_VALUE-1) == -Integer.MAX_VALUE-1
                hash = 0;
            }
            return hash;
        }
    }

    private SOURCE _source;
    private SOURCE _sourceIPv4;
    private SOURCE _sourceIPv6;
    // END fields for SOURCE

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
    public SvrHandleConnector next(IPPort source) {
        if (method == Method.wrr) {
            return wrrNext();
        } else if (method == Method.wlc) {
            return wlcNext();
        } else if (method == Method.source) {
            return sourceHashGet(source.getAddress());
        } else {
            Logger.shouldNotHappen("unsupported method " + method);
            // use wrr instead
            return wrrNext();
        }
    }

    public SvrHandleConnector nextIPv4(IPPort source) {
        if (method == Method.wrr) {
            return wrrNextIPv4();
        } else if (method == Method.wlc) {
            return wlcNextIPv4();
        } else if (method == Method.source) {
            return sourceHashGetIPv4(source.getAddress());
        } else {
            Logger.shouldNotHappen("unsupported method " + method);
            // use wrr instead
            return wrrNextIPv4();
        }
    }

    public SvrHandleConnector nextIPv6(IPPort source) {
        if (method == Method.wrr) {
            return wrrNextIPv6();
        } else if (method == Method.wlc) {
            return wlcNextIPv6();
        } else if (method == Method.source) {
            return sourceHashGetIPv6(source.getAddress());
        } else {
            Logger.shouldNotHappen("unsupported method " + method);
            // use wrr instead
            return wrrNextIPv6();
        }
    }

    private SvrHandleConnector sourceHashGet(IP source) {
        byte[] bytes = source.getAddress();
        return sourceHashGet(_source, _source.hash(bytes), 0);
    }

    private SvrHandleConnector sourceHashGetIPv4(IP source) {
        byte[] bytes = source.getAddress();
        return sourceHashGet(_sourceIPv4, _sourceIPv4.hash(bytes), 0);
    }

    private SvrHandleConnector sourceHashGetIPv6(IP source) {
        byte[] bytes = source.getAddress();
        return sourceHashGet(_sourceIPv6, _sourceIPv6.hash(bytes), 0);
    }

    private SvrHandleConnector sourceHashGet(SOURCE source, int hash, int recurse) {
        if (recurse >= source.servers.size()) // this condition also checks empty state
            return null;

        int idx = hash % source.servers.size();
        ServerHandle h = source.servers.get(idx);
        if (h.healthy)
            return h.makeConnector();

        // increase the "hash" by 1, which means using the next server in the list
        return sourceHashGet(source, idx + 1, recurse + 1);
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

    private SvrHandleConnector wlcNext() {
        return wlcNext(_wlc, 0);
    }

    private SvrHandleConnector wlcNextIPv4() {
        return wlcNext(_wlcIPv4, 0);
    }

    private SvrHandleConnector wlcNextIPv6() {
        return wlcNext(_wlcIPv6, 0);
    }

    private SvrHandleConnector wlcNext(WLC wlc, int mStart) {
        if (mStart >= wlc.servers.size())
            return null;

        if (wlc.servers.isEmpty())
            return null;
        int m, n, WSm, CSm, WSi, CSi;
        ServerHandle Sm;
        m = mStart;
        n = wlc.servers.size();
        // for (m = 0; m < n; ++m) {
        { // --------- START ---------
            Sm = wlc.servers.get(m);
            WSm = Sm.weight;
            CSm = Sm.connectionCount();
        } // --------- END ---------
        if (!Sm.healthy) {
            return wlcNext(wlc, mStart + 1);
        }
        // if (WSm > 0) {
        for (int i = m + 1; i < n; ++i) {
            ServerHandle Si = wlc.servers.get(i);
            WSi = Si.weight;
            CSi = Si.connectionCount();
            if (CSm * WSi > CSi * WSm && Si.healthy) {
                m = i;
                { // --------- START ---------
                    Sm = wlc.servers.get(m);
                    WSm = Sm.weight;
                    CSm = Sm.connectionCount();
                } // --------- END ---------
            }
        }
        return Sm.makeConnector();
        // }
        // }
        // return null;
    }

    private SvrHandleConnector wrrNext() {
        return wrrNext(this._wrr, 0);
    }

    private SvrHandleConnector wrrNextIPv4() {
        return wrrNext(this._wrrIPv4, 0);
    }

    private SvrHandleConnector wrrNextIPv6() {
        return wrrNext(this._wrrIPv6, 0);
    }

    private SvrHandleConnector wrrNext(WRR wrr, int recursion) {
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
            return h.makeConnector();
        else
            return wrrNext(wrr, recursion + 1);
    }

    private void resetMethodRelatedFields() {
        wrrReset();
        wlcReset();
        sourceReset();
    }

    private int gcd(int a, int b) {
        if (a == b) return a;
        if (a > b) return gcd(a - b, b);
        return gcd(b - a, a);
    }

    private void sourceReset() {
        _source = sourceReset(servers);
        _sourceIPv4 = sourceReset(servers.stream().filter(s -> s.server.getAddress() instanceof IPv4).collect(Collectors.toList()));
        _sourceIPv6 = sourceReset(servers.stream().filter(s -> s.server.getAddress() instanceof IPv6).collect(Collectors.toList()));
    }

    private SOURCE sourceReset(List<ServerHandle> servers) {
        //noinspection FuseStreamOperations
        ArrayList<ServerHandle> svrs = new ArrayList<>(servers.stream().filter(s -> s.weight > 0).collect(Collectors.toList()));
        svrs.sort((a, b) -> {
            byte[] ba = a.server.getAddress().getAddress();
            byte[] bb = b.server.getAddress().getAddress();
            if (ba.length > bb.length)
                return 1;
            if (bb.length > ba.length)
                return -1;
            for (int i = 0; i < ba.length; ++i) {
                int diff = ba[i] - bb[i];
                if (diff != 0)
                    return diff;
            }
            return a.server.getPort() - b.server.getPort();
        });
        if (svrs.size() == 0) {
            return new SOURCE(new int[0], svrs);
        }
        int g = svrs.size() > 1
            ? gcd(svrs.get(0).weight, svrs.get(1).weight)
            : svrs.get(0).weight;
        LinkedList<Integer> seqList = new LinkedList<>();
        for (int sIdx = 0; sIdx < svrs.size(); sIdx++) {
            ServerHandle s = svrs.get(sIdx);
            int w = s.weight;
            int times = w / g;
            for (int i = 0; i < times; ++i) {
                seqList.add(sIdx);
            }
        }
        int[] seq = new int[seqList.size()];
        int idx = 0;
        for (Integer integer : seqList) {
            seq[idx++] = integer;
        }
        return new SOURCE(seq, svrs);
    }

    private void wlcReset() {
        this._wlc = new WLC(this.servers.stream().filter(s -> s.weight > 0).collect(Collectors.toList()));
        this._wlcIPv4 = new WLC(this.servers.stream()
            .filter(s -> s.weight > 0)
            .filter(s -> s.server.getAddress() instanceof IPv4)
            .collect(Collectors.toList()));
        this._wlcIPv6 = new WLC(this.servers.stream()
            .filter(s -> s.weight > 0)
            .filter(s -> s.server.getAddress() instanceof IPv6)
            .collect(Collectors.toList()));
    }

    private void wrrReset() {
        this._wrr = wrrReset(new WRR(this.servers.stream()
            .filter(s -> s.weight > 0) // only consider those weight > 0
            .collect(Collectors.toList())));
        this._wrrIPv4 = wrrReset(new WRR(this.servers.stream()
            .filter(s -> s.weight > 0)
            .filter(s -> s.server.getAddress() instanceof IPv4)
            .collect(Collectors.toList())));
        this._wrrIPv6 = wrrReset(new WRR(this.servers.stream()
            .filter(s -> s.weight > 0)
            .filter(s -> s.server.getAddress() instanceof IPv6)
            .collect(Collectors.toList())));
    }

    private WRR wrrReset(WRR wrr) {
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
            //noinspection Duplicates
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

        return wrr;
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

    public Annotations getAnnotations() {
        return annotations;
    }

    public void setAnnotations(Annotations annotations) {
        if (annotations == null) {
            annotations = new Annotations();
        }
        this.annotations = annotations;

        // set hc client annotations
        annotatedHcConfig.set(annotations);
    }

    public synchronized ServerHandle add(String alias, IPPort server, int weight) throws AlreadyExistException {
        return add(alias, null, server, weight);
    }

    public synchronized ServerHandle add(String alias, /*nullable*/ String hostName, IPPort server, int weight) throws AlreadyExistException {
        return add(alias, hostName, false, server, weight);
    }

    public synchronized void replaceIp(String alias, IP newIp) throws NotFoundException {
        // find the server to replace
        ServerHandle toReplace = null;
        ArrayList<ServerHandle> list = servers;
        for (ServerHandle h : list) {
            if (h.logicDelete) // ignore logic deleted servers
                continue;
            if (h.alias.equals(alias)) {
                toReplace = h;
                break;
            }
        }
        if (toReplace == null)
            throw new NotFoundException("server in server-group " + ServerGroup.this.alias, alias);
        // do replace
        try {
            add(alias, toReplace.hostName, true,
                new IPPort(newIp, toReplace.server.getPort()),
                toReplace.weight);
        } catch (AlreadyExistException e) {
            // should not raise the error
            Logger.shouldNotHappen("should not raise AlreadyExist when replace", e);
        }
    }

    /**
     * this field is only used when adding, for debug purpose only
     */
    private final AtomicLong idForServer = new AtomicLong(0);

    /**
     * add a server into the group
     *
     * @param alias    server alias
     * @param hostName host name to be resolved, can be null if it's an ip.
     *                 and will be set to null if it's an ip even it's provided
     * @param replace  if true, it will try to replace an existing server.
     *                 if true and the server with same alias not found, it will simply add.
     *                 the old server will be set to weight 0 and logic delete and will be removed when no connections
     * @param server   ip:port, ip is resolved
     * @param weight   server weight
     * @throws AlreadyExistException already exists
     */
    private synchronized ServerHandle add(String alias, String hostName, boolean replace, IPPort server, int weight) throws AlreadyExistException {
        // set the hostName to null if it's an ip literal
        if (hostName != null && IP.isIpLiteral(hostName))
            hostName = null;

        // the server which will be logic deleted
        // this server will be removed when the new server is UP
        // and will remove the `logicDelete` flag if new server is DOWN
        // will be null if alias not found or `replace` is set to false
        ServerHandle toLogicDelete = null;

        ArrayList<ServerHandle> ls = servers;
        for (ServerHandle c : ls) {
            if (c.alias.equals(alias)) {
                if (c.logicDelete) // ignore logic deleted servers
                    continue;
                if (!replace) // raise error if replace flag is disabled
                    throw new AlreadyExistException("server in server-group " + ServerGroup.this.alias, alias);
                toLogicDelete = c;
                // directly set the flag
                c.logicDelete = true;
            }
        }

        // attach new server
        ServerHandle handle = new ServerHandle(
            alias, idForServer.getAndIncrement(), hostName, server, weight, toLogicDelete);
        handle.start();
        ArrayList<ServerHandle> newLs = new ArrayList<>(ls.size() + 1);
        newLs.addAll(ls);
        newLs.add(handle);
        servers = newLs;
        resetMethodRelatedFields();

        assert Logger.lowLevelDebug("server added: " + alias + "(" + server + ") to " + this.alias);

        return handle;
    }

    public synchronized void remove(String alias) throws NotFoundException {
        ArrayList<ServerHandle> ls = servers;
        if (ls.isEmpty())
            throw new NotFoundException("server in server-group " + ServerGroup.this.alias, alias);
        ArrayList<ServerHandle> newLs = new ArrayList<>(servers.size() - 1);
        boolean found = false;
        for (ServerHandle c : ls) {
            if (c.alias.equals(alias)) {
                // here may remove multiple servers
                // with the same alias
                found = true;
                c.stop();
            } else {
                newLs.add(c);
            }
        }
        if (!found)
            throw new NotFoundException("server in server-group " + ServerGroup.this.alias, alias);
        servers = newLs;
        resetMethodRelatedFields();

        assert Logger.lowLevelDebug("server removed " + alias + " from " + this.alias);
    }

    // this method should do exactly the same as `remove()`
    // but only remove one serverHandle and do not raise error
    private synchronized void remove(ServerHandle h) {
        ArrayList<ServerHandle> ls = servers;
        if (ls.isEmpty())
            return;
        ArrayList<ServerHandle> newLs = new ArrayList<>(servers.size() - 1);
        boolean found = false;
        for (ServerHandle c : ls) {
            if (c == h) {
                found = true;
                c.stop();
            } else {
                newLs.add(c);
            }
        }
        if (found) {
            // only replace servers when found
            servers = newLs;
            resetMethodRelatedFields();
        }

        assert Logger.lowLevelDebug("server handle removed " + h.alias + "(" + h.sid + ") from " + this.alias);
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

    public void addServerListener(ServerListener serverListener) {
        this.serverListeners.add(serverListener);
    }

    public void removeServerListener(ServerListener serverListener) {
        this.serverListeners.remove(serverListener);
    }

    public List<ServerHandle> getServerHandles() {
        return new ArrayList<>(servers);
    }
}
