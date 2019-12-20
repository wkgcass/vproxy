package vproxy.dns;

import vfd.FDProvider;
import vproxy.selector.TimerEvent;
import vproxy.util.LogType;
import vproxy.util.Logger;
import vproxy.util.Tuple;
import vproxy.util.Utils;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Cache {
    private AbstractResolver abstractResolver;
    public final String host;
    public final List<Inet4Address> ipv4;
    public final List<Inet6Address> ipv6;
    private final AtomicInteger idxIpv4 = new AtomicInteger(0);
    private final AtomicInteger idxIpv6 = new AtomicInteger(0);
    final TimerEvent te;
    public final long timestamp;

    Cache(AbstractResolver abstractResolver, String host, InetAddress[] addresses) {
        this.abstractResolver = abstractResolver;
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

        if (abstractResolver.ttl > 0) {
            // start a timer to clear the record
            te = abstractResolver.loop.getSelectorEventLoop().delay(abstractResolver.ttl, Cache.this::remove);
        } else {
            te = null;
        }

        timestamp = FDProvider.get().currentTimeMillis();
    }

    public void remove() {
        if (te != null) {
            te.cancel();
        }
        assert Logger.lowLevelDebug("cache removed " + host);
        abstractResolver.cacheMap.remove(host);

        for (ResolveListener lsn : abstractResolver.resolveListeners) {
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
