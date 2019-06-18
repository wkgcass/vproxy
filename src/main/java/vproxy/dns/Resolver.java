package vproxy.dns;

import vproxy.connection.NetEventLoop;
import vproxy.selector.SelectorEventLoop;
import vproxy.selector.TimerEvent;
import vproxy.util.*;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

public class Resolver implements IResolver {
    static class ResolveTask {
        final String host;
        final Callback<InetAddress, UnknownHostException> cb;
        final boolean ipv4;
        final boolean ipv6;

        ResolveTask(String host, Callback<InetAddress, UnknownHostException> cb,
                    boolean ipv4, boolean ipv6) {
            this.host = host;
            this.cb = cb;
            this.ipv4 = ipv4;
            this.ipv6 = ipv6;
        }
    }

    public class Cache {
        public final String host;
        public final List<Inet4Address> ipv4;
        public final List<Inet6Address> ipv6;
        private final AtomicInteger idxIpv4 = new AtomicInteger(0);
        private final AtomicInteger idxIpv6 = new AtomicInteger(0);
        final TimerEvent te;
        public final long timestamp;

        Cache(String host, InetAddress[] addresses) {
            this.host = host;
            List<Inet4Address> ipv4 = new LinkedList<>();
            List<Inet6Address> ipv6 = new LinkedList<>();
            for (InetAddress a : addresses) {
                if (a instanceof Inet4Address) {
                    ipv4.add((Inet4Address) a);
                } else if (a instanceof Inet6Address) {
                    ipv6.add((Inet6Address) a);
                }
            }
            this.ipv4 = Collections.unmodifiableList(ipv4);
            this.ipv6 = Collections.unmodifiableList(ipv6);

            if (ttl > 0) {
                // start a timer to clear the record
                te = loop.getSelectorEventLoop().delay(ttl, Cache.this::remove);
            } else {
                te = null;
            }

            timestamp = System.currentTimeMillis();
        }

        public void remove() {
            if (te != null) {
                te.cancel();
            }
            assert Logger.lowLevelDebug("cache removed " + host);
            cacheMap.remove(host);

            for (ResolveListener lsn : resolveListeners) {
                try {
                    lsn.onRemove(this);
                } catch (Throwable t) {
                    Logger.error(LogType.IMPROPER_USE, "onRemove() raised exception", t);
                }
            }
        }

        public Tuple<Inet4Address, Inet6Address> next() {
            Inet4Address v4 = null;
            Inet6Address v6 = null;
            //noinspection Duplicates
            if (ipv4.size() != 0) {
                int idx = idxIpv4.getAndIncrement();
                if (idx >= ipv4.size()) {
                    idx = idx % ipv4.size();
                    idxIpv4.set(idx + 1);
                }
                v4 = ipv4.get(idx);
            }
            //noinspection Duplicates
            if (ipv6.size() != 0) {
                int idx = idxIpv6.getAndIncrement();
                if (idx >= ipv6.size()) {
                    idx = idx % ipv6.size();
                    idxIpv6.set(idx + 1);
                }
                v6 = ipv6.get(idx);
            }
            return new Tuple<>(v4, v6);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(host).append(" -> ipv4 [");
            boolean isFirst = true;
            for (Inet4Address i : ipv4) {
                if (isFirst) isFirst = false;
                else sb.append(",");
                sb.append(Utils.ipStr(i.getAddress()));
            }
            sb.append("] ipv6 [");
            isFirst = true;
            for (Inet6Address i : ipv6) {
                if (isFirst) isFirst = false;
                else sb.append(",");
                sb.append(Utils.ipStr(i.getAddress()));
            }
            return sb.toString();
        }
    }

    private static volatile Resolver defaultResolver;

    public static IResolver getDefault() {
        if (defaultResolver != null)
            return defaultResolver;
        synchronized (Resolver.class) {
            if (defaultResolver != null)
                return defaultResolver;
            try {
                defaultResolver = new Resolver("Resolver");
            } catch (IOException e) {
                throw new RuntimeException("create resolver failed");
            }
            defaultResolver.start();

            return defaultResolver;
        }
    }

    public static void stopDefault() {
        Resolver r;
        synchronized (Resolver.class) {
            r = defaultResolver;
            defaultResolver = null;
        }
        if (r == null)
            return;
        try {
            r.stop();
        } catch (IOException e) {
            // we can do nothing about it, just log
            Logger.shouldNotHappen("close resolver failed", e);
        }
    }

    private final String alias;
    private final NetEventLoop loop;
    public int ttl = 60000;
    private final ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>();
    private final CopyOnWriteArraySet<ResolveListener> resolveListeners = new CopyOnWriteArraySet<>();

    public Resolver(String alias) throws IOException {
        // currently we only use java standard lib to resolve the address
        // so this loop is only used for handling events for now
        this.alias = alias;
        this.loop = new NetEventLoop(SelectorEventLoop.open());
        // java resolve process will block the thread
        // so we start a new thread only for resolving
        // it will make a callback when resolve completed
        // let's just handle it in the loop since it is created for resolving
    }

    public void start() {
        loop.getSelectorEventLoop().loop(r -> new Thread(r, alias));
    }

    private void doResolve(ResolveTask task) {
        // handle the task
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(task.host);
        } catch (UnknownHostException e) {
            // got exception, let's call the callback
            task.cb.failed(e);
            return;
        }
        // record
        if (addresses.length > 0) {
            Cache cache = new Cache(task.host, addresses);
            assert Logger.lowLevelDebug("cache recorded " + cache.host);
            cacheMap.put(task.host, cache);
            for (ResolveListener lsn : resolveListeners) {
                try {
                    lsn.onResolve(cache);
                } catch (Throwable t) {
                    // we can do nothing about it
                    Logger.error(LogType.IMPROPER_USE, "onResolve() raised exception", t);
                }
            }
        }

        // filter the result
        InetAddress result = filter(addresses, task.ipv4, task.ipv6);
        if (result != null) {
            task.cb.succeeded(result);
            return;
        }

        // otherwise nothing can be returned
        // we raise exception
        task.cb.failed(new UnknownHostException(task.host));
    }

    private InetAddress filter(InetAddress[] addresses, boolean ipv4, boolean ipv6) {
        // get first returned ipv4 and ipv6
        Inet4Address ipv4Addr = null;
        Inet6Address ipv6Addr = null;
        for (InetAddress addr : addresses) {
            if (ipv4Addr != null && ipv6Addr != null)
                break;
            if (addr instanceof Inet4Address && ipv4Addr == null)
                ipv4Addr = (Inet4Address) addr;
            else if (addr instanceof Inet6Address && ipv6Addr == null)
                ipv6Addr = (Inet6Address) addr;
        }
        // check required and callback
        // if ipv4 is allowed, return the ipv4
        // because ipv4 is always supported
        if (ipv4 && ipv4Addr != null) {
            return ipv4Addr;
        }
        // then return ipv6 if allowed
        if (ipv6 && ipv6Addr != null) {
            return ipv6Addr;
        }
        // otherwise return nothing
        return null;
    }

    @SuppressWarnings("unchecked")
    private void resolveN(String host, boolean ipv4, boolean ipv6, Callback<? super InetAddress, ? super UnknownHostException> cb) {
        // check whether it's ipv4 or ipv6
        if (Utils.isIpv4(host)) {
            if (ipv4) {
                Inet4Address addr;
                try {
                    addr = (Inet4Address) InetAddress.getByName(host);
                } catch (UnknownHostException e) {
                    // should not happen
                    Logger.shouldNotHappen("resolving an ipv4 address string should success");
                    cb.failed(e);
                    return;
                }
                cb.succeeded(addr);
            } else {
                cb.failed(new UnknownHostException(host));
            }
            return;
        } else if (Utils.isIpv6(host)) {
            if (ipv6) {
                Inet6Address addr;
                try {
                    addr = (Inet6Address) InetAddress.getByName(host);
                } catch (UnknownHostException e) {
                    // should not happen
                    Logger.shouldNotHappen("resolving an ipv6 address string should success");
                    cb.failed(e);
                    return;
                }
                cb.succeeded(addr);
            } else {
                cb.failed(new UnknownHostException(host));
            }
            return;
        }

        // it's not ip literal
        // let's resolve
        Cache r = cacheMap.get(host);
        if (r == null) {
            loop.getSelectorEventLoop().runOnLoop(() ->
                doResolve(new ResolveTask(host, (Callback) cb, ipv4, ipv6)));
            return;
        }
        Tuple<Inet4Address, Inet6Address> tup = r.next();
        Inet4Address v4 = tup.left;
        Inet6Address v6 = tup.right;
        @SuppressWarnings("UnnecessaryLocalVariable")
        Callback rawCB = cb; // to suppress compile error
        // check ipv4 first
        // see comments in filter()
        if (ipv4 && v4 != null) {
            rawCB.succeeded(v4);
            return;
        }
        if (ipv6 && v6 != null) {
            rawCB.succeeded(v6);
            return;
        }
        // otherwise not found
        cb.failed(new UnknownHostException(host));
    }

    @Override
    public void resolve(String host, Callback<? super InetAddress, ? super UnknownHostException> cb) {
        resolveN(host, true, true, cb);
    }

    @Override
    public void resolve(String host, boolean ipv4, boolean ipv6, Callback<? super InetAddress, ? super UnknownHostException> cb) {
        resolveN(host, ipv4, ipv6, cb);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void resolveV6(String host, Callback<? super Inet6Address, ? super UnknownHostException> cb) {
        resolveN(host, false, true, (Callback) cb);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void resolveV4(String host, Callback<? super Inet4Address, ? super UnknownHostException> cb) {
        resolveN(host, true, false, (Callback) cb);
    }

    @Override
    public int cacheCount() {
        return cacheMap.size();
    }

    @Override
    public void copyCache(Collection<? super Cache> cacheList) {
        cacheList.addAll(this.cacheMap.values());
    }

    public void clearCache() {
        for (Cache c : cacheMap.values()) {
            c.remove();
        }
    }

    public void addListener(ResolveListener lsn) {
        resolveListeners.add(lsn);
    }

    @Blocking
    public void stop() throws IOException {
        loop.getSelectorEventLoop().close();
        clearCache();
    }
}
