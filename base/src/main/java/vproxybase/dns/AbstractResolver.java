package vproxybase.dns;

import vfd.*;
import vfd.jdk.ChannelFDs;
import vproxybase.connection.NetEventLoop;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.util.*;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

public abstract class AbstractResolver implements Resolver {
    static class ResolveTask {
        final String host;
        final Callback<IP, UnknownHostException> cb;
        final boolean ipv4;
        final boolean ipv6;

        ResolveTask(String host, Callback<IP, UnknownHostException> cb,
                    boolean ipv4, boolean ipv6) {
            this.host = host;
            this.cb = cb;
            this.ipv4 = ipv4;
            this.ipv6 = ipv6;
        }
    }

    private static volatile AbstractResolver defaultResolver;

    static long fileNameServerUpdateTimestamp = 0; // set this field to -1 to indicate that the file not exists
    static long fileHostUpdateTimestamp = 0;

    static Resolver getDefault() {
        if (defaultResolver != null)
            return defaultResolver;
        synchronized (AbstractResolver.class) {
            if (defaultResolver != null)
                return defaultResolver;
            // the fstack is usually exposed to public network
            // and services usually do dns resolving in the idc network, which will fail if use fstack to do resolve
            // so we start the resolver using traditional network stack
            FDs fds;
            if (VFDConfig.useFStack) {
                fds = ChannelFDs.get();
            } else {
                fds = FDProvider.get().getProvided();
            }
            try {
                defaultResolver = new VResolver("Resolver", fds);
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

    protected AbstractResolver(String alias, FDs fds) throws IOException {
        // currently we only use java standard lib to resolve the address
        // so this loop is only used for handling events for now
        this.alias = alias;
        this.loop = new NetEventLoop(SelectorEventLoop.open(fds));
        // java resolve process will block the thread
        // so we start a new thread only for resolving
        // it will make a callback when resolve completed
        // let's just handle it in the loop since it is created for resolving
    }

    public NetEventLoop getLoop() {
        return loop;
    }

    @Override
    public void start() {
        loop.getSelectorEventLoop().loop(r -> new Thread(r, alias));
    }

    abstract protected void getAllByName(String domain, Callback<IP[], UnknownHostException> cb);

    private void doResolve(ResolveTask task) {
        getAllByName(task.host, new Callback<>() {
            @Override
            protected void onSucceeded(IP[] addresses) {
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
                IP result = filter(addresses, task.ipv4, task.ipv6);
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

    private IP filter(IP[] addresses, boolean ipv4, boolean ipv6) {
        // get first returned ipv4 and ipv6
        IPv4 ipv4Addr = null;
        IPv6 ipv6Addr = null;
        for (IP addr : addresses) {
            if (ipv4Addr != null && ipv6Addr != null)
                break;
            if (addr instanceof IPv4 && ipv4Addr == null)
                ipv4Addr = (IPv4) addr;
            else if (addr instanceof IPv6 && ipv6Addr == null)
                ipv6Addr = (IPv6) addr;
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
    private void resolveN(String host, boolean ipv4, boolean ipv6, Callback<? super IP, ? super UnknownHostException> cb) {
        // check whether it's ipv4 or ipv6
        if (IP.isIpv4(host)) {
            if (ipv4) {
                IPv4 addr = IP.fromIPv4(host);
                cb.succeeded(addr);
            } else {
                cb.failed(new UnknownHostException(host));
            }
            return;
        } else if (IP.isIpv6(host)) {
            if (ipv6) {
                IPv6 addr = IP.fromIPv6(host);
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
                doResolve(new ResolveTask(host, new RunOnLoopCallback<>((Callback) cb), ipv4, ipv6)));
            return;
        }
        Tuple<IPv4, IPv6> tup = r.next();
        IPv4 v4 = tup.left;
        IPv6 v6 = tup.right;
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
    public void resolve(String host, Callback<? super IP, ? super UnknownHostException> cb) {
        resolveN(host, true, true, cb);
    }

    @Override
    public void resolve(String host, boolean ipv4, boolean ipv6, Callback<? super IP, ? super UnknownHostException> cb) {
        resolveN(host, ipv4, ipv6, cb);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void resolveV6(String host, Callback<? super IPv6, ? super UnknownHostException> cb) {
        resolveN(host, false, true, (Callback) cb);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void resolveV4(String host, Callback<? super IPv4, ? super UnknownHostException> cb) {
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
