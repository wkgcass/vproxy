package io.vproxy.base.dns;

import io.vproxy.base.selector.TimerEvent;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.coll.Tuple;
import io.vproxy.vfd.FDProvider;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPv4;
import io.vproxy.vfd.IPv6;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Cache {
    private AbstractResolver abstractResolver;
    public final String host;
    public final List<IPv4> ipv4;
    public final List<IPv6> ipv6;
    private final AtomicInteger idxIpv4 = new AtomicInteger(0);
    private final AtomicInteger idxIpv6 = new AtomicInteger(0);
    final TimerEvent te;
    public final long timestamp;

    Cache(AbstractResolver abstractResolver, String host, IP[] addresses) {
        this.abstractResolver = abstractResolver;
        this.host = host;
        List<IPv4> ipv4 = new LinkedList<>();
        List<IPv6> ipv6 = new LinkedList<>();
        for (IP a : addresses) {
            if (a instanceof IPv4) {
                ipv4.add((IPv4) a);
            } else if (a instanceof IPv6) {
                ipv6.add((IPv6) a);
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

    public Tuple<IPv4, IPv6> next() {
        IPv4 v4 = null;
        IPv6 v6 = null;
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
        for (IPv4 i : ipv4) {
            if (isFirst) isFirst = false;
            else sb.append(",");
            sb.append(i.formatToIPString());
        }
        sb.append("] ipv6 [");
        isFirst = true;
        for (IPv6 i : ipv6) {
            if (isFirst) isFirst = false;
            else sb.append(",");
            sb.append(i.formatToIPString());
        }
        return sb.toString();
    }
}
