package vproxy.dns;

import vfd.FDProvider;
import vfd.VFDConfig;
import vfd.jdk.ChannelFDs;
import vproxy.connection.NetEventLoop;
import vproxy.dns.rdata.JDKResolver;
import vproxy.selector.SelectorEventLoop;
import vproxy.util.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

public abstract class AbstractResolver implements Resolver {
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

    private static volatile AbstractResolver defaultResolver;

    static Resolver getDefault() {
        if (defaultResolver != null)
            return defaultResolver;
        synchronized (AbstractResolver.class) {
            if (defaultResolver != null)
                return defaultResolver;
            List<InetSocketAddress> nameServers = Resolver.getNameServers();
            try {
                String name = "Resolver";
                if (nameServers.isEmpty()) {
                    defaultResolver = new JDKResolver(name);
                } else {
                    defaultResolver = new VResolver(name, nameServers, Resolver.getHosts());
                }
            } catch (IOException e) {
                throw new RuntimeException("create resolver failed");
            }
            defaultResolver.start();

            return defaultResolver;
        }
    }

    static void stopDefault() {
        AbstractResolver r;
        synchronized (AbstractResolver.class) {
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

    public final String alias;
    protected final NetEventLoop loop;
    public int ttl = 60000;
    final ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>();
    final CopyOnWriteArraySet<ResolveListener> resolveListeners = new CopyOnWriteArraySet<>();

    public AbstractResolver(String alias) throws IOException {
        // currently we only use java standard lib to resolve the address
        // so this loop is only used for handling events for now
        this.alias = alias;
        this.loop = new NetEventLoop(SelectorEventLoop.open(
            VFDConfig.useFStack
                ? ChannelFDs.get()
                : FDProvider.get().getProvided()
            /*FIXME: we might implement nonblocking dns client, this can be modified at that time*/));
        // java resolve process will block the thread
        // so we start a new thread only for resolving
        // it will make a callback when resolve completed
        // let's just handle it in the loop since it is created for resolving
    }

    @Override
    public void start() {
        loop.getSelectorEventLoop().loop(r -> new Thread(r, alias));
    }

    abstract protected void getAllByName(String domain, Callback<InetAddress[], UnknownHostException> cb);

    private void doResolve(ResolveTask task) {
        getAllByName(task.host, new Callback<>() {
            @Override
            protected void onSucceeded(InetAddress[] addresses) {
                // record
                if (addresses.length > 0) {
                    Cache cache = new Cache(AbstractResolver.this, task.host, addresses);
                    assert Logger.lowLevelDebug("cache recorded " + cache.host + " -> " + Arrays.toString(addresses));
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

            @Override
            protected void onFailed(UnknownHostException err) {
                task.cb.failed(err);
            }
        });
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

    @Override
    public void clearCache() {
        for (Cache c : cacheMap.values()) {
            c.remove();
        }
    }

    @Override
    public void addListener(ResolveListener lsn) {
        resolveListeners.add(lsn);
    }

    @Override
    @Blocking
    public void stop() throws IOException {
        loop.getSelectorEventLoop().close();
        clearCache();
    }
}
