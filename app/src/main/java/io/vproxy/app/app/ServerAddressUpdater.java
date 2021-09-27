package io.vproxy.app.app;

import io.vproxy.base.component.svrgroup.ServerGroup;
import io.vproxy.base.dns.Cache;
import io.vproxy.base.dns.ResolveListener;
import io.vproxy.base.dns.Resolver;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.callback.Callback;
import vproxy.base.util.coll.Tuple;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.vfd.FDProvider;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPv4;
import io.vproxy.vfd.IPv6;

import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ServerAddressUpdater implements ResolveListener {
    private static ServerAddressUpdater updater = new ServerAddressUpdater();

    private ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>();

    private ServerAddressUpdater() {
    }

    public static void init() {
        Resolver resolver = Resolver.getDefault();
        resolver.addListener(updater);
    }

    @Override
    public void onResolve(Cache cache) {
        Cache old = cacheMap.put(cache.host, cache);
        if (old == null) {
            // nothing to handle
            return;
        }
        handle(cache, old);
    }

    private static void handle(Cache newCache, Cache oldCache) {
        long cur = FDProvider.get().currentTimeMillis();
        if (cur - oldCache.timestamp > 3600_000) { // scan the whole list every one hour
            checkAll(newCache.host, newCache);
        } else {
            checkMissing(newCache.host, newCache, oldCache);
        }
    }

    private static void checkAll(String host, Cache c) {
        Set<IP> addresses = new HashSet<>();
        addresses.addAll(c.ipv4);
        addresses.addAll(c.ipv6);

        List<String> groupNames = Application.get().serverGroupHolder.names();

        for (String groupName : groupNames) {
            ServerGroup grp;
            try {
                grp = Application.get().serverGroupHolder.get(groupName);
            } catch (NotFoundException ignore) {
                // ignore if it's deleted
                continue;
            }

            List<ServerGroup.ServerHandle> handles = grp.getServerHandles();
            for (ServerGroup.ServerHandle h : handles) {
                // host name matches, and the address is not in new record
                if (host.equals(/*may be null*/h.hostName) && !addresses.contains(h.server.getAddress())) {
                    doReplace(grp, c, h);
                }
            }
        }
    }

    private static void checkMissing(String host, Cache newCache, Cache oldCache) {
        Set<IP> missing = new HashSet<>();
        for (IPv4 n : oldCache.ipv4) {
            if (!newCache.ipv4.contains(n)) {
                // in old, but not in new
                missing.add(n);
            }
        }
        for (IPv6 n : oldCache.ipv6) {
            if (!newCache.ipv6.contains(n)) {
                // in old, but not in new
                missing.add(n);
            }
        }
        if (!missing.isEmpty()) {
            handleMissing(host, newCache, missing);
        }
    }

    private static void handleMissing(String host, Cache c, Set<IP> missing) {
        List<String> groupNames = Application.get().serverGroupHolder.names();

        for (String groupName : groupNames) {
            ServerGroup grp;
            try {
                grp = Application.get().serverGroupHolder.get(groupName);
            } catch (NotFoundException ignore) {
                // ignore if it's deleted
                continue;
            }

            List<ServerGroup.ServerHandle> handles = grp.getServerHandles();
            for (ServerGroup.ServerHandle h : handles) {
                // host name matches, and the address is missing
                if (host.equals(/*may be null*/h.hostName) && missing.contains(h.server.getAddress())) {
                    doReplace(grp, c, h);
                }
            }
        }
    }

    private static void doReplace(ServerGroup grp, Cache c, ServerGroup.ServerHandle h) {
        Tuple<IPv4, IPv6> tup = c.next();
        if (h.server.getAddress() instanceof IPv4) {
            if (tup.left != null) {
                Logger.info(LogType.RESOLVE_REPLACE,
                    "replace grp=" + grp.alias +
                        ", server=" + h.alias +
                        ", old=" + h.server.getAddress() +
                        ", new=" + tup.left);
                try {
                    grp.replaceIp(h.alias, tup.left);
                } catch (NotFoundException ignore) {
                    // ignore if it's deleted
                }
            }
        } else if (h.server.getAddress() instanceof IPv6) {
            if (tup.right != null) {
                Logger.info(LogType.RESOLVE_REPLACE,
                    "replace grp=" + grp.alias +
                        ", server=" + h.alias +
                        ", old=" + h.server.getAddress() +
                        ", new=" + tup.right);
                try {
                    grp.replaceIp(h.alias, tup.right);
                } catch (NotFoundException ignore) {
                    // ignore if it's deleted
                }
            }
        } // else should not happen, we ignore
    }

    @Override
    public void onRemove(Cache cache) {
        // re-resolve it

        String host = cache.host;
        Resolver.getDefault().resolve(host, new Callback<>() {
            @Override
            protected void onSucceeded(IP value) {
                // ignore
            }

            @Override
            protected void onFailed(UnknownHostException err) {
                // ignore
            }
        });
    }
}
