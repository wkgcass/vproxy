package vproxy.vpacket.conntrack;

import vproxy.base.util.LogType;
import vproxy.base.util.Logger;
import vproxy.vfd.IP;
import vproxy.vfd.IPPort;
import vproxy.vfd.IPv4;
import vproxy.vpacket.conntrack.tcp.ListenEntry;
import vproxy.vpacket.conntrack.tcp.ListenHandler;
import vproxy.vpacket.conntrack.tcp.TcpEntry;

import java.util.*;

public class Conntrack {
    private final Map<IPPort, ListenEntry> listenEntries = new HashMap<>();
    // dstIPPort => srcIPPort => TcpEntry
    // make dstIPPort to be the first key for better performance
    private final Map<IPPort, Map<IPPort, TcpEntry>> connectionEntries = new HashMap<>();

    private static final IP ipv4BindAny = IP.from("0.0.0.0");
    private static final IP ipv6BindAny = IP.from("::");

    public int countListenEntry() {
        return listenEntries.size();
    }

    public Collection<ListenEntry> listListenEntries() {
        return listenEntries.values();
    }

    public int countTcpEntries() {
        int total = 0;
        for (var map : connectionEntries.values()) {
            total += map.size();
        }
        return total;
    }

    public Collection<TcpEntry> listTcpEntries() {
        List<TcpEntry> ls = new LinkedList<>();
        for (var map : connectionEntries.values()) {
            ls.addAll(map.values());
        }
        return ls;
    }

    public ListenEntry lookupListen(IPPort dst) {
        var ret = listenEntries.get(dst);
        if (ret != null) {
            return ret;
        }
        // search for wildcard
        if (dst.getAddress() instanceof IPv4) {
            return listenEntries.get(new IPPort(ipv4BindAny, dst.getPort()));
        } else {
            return listenEntries.get(new IPPort(ipv6BindAny, dst.getPort()));
        }
    }

    public TcpEntry lookup(IPPort src, IPPort dst) {
        var map = connectionEntries.get(dst);
        if (map == null) {
            return null;
        }
        return map.get(src);
    }

    public TcpEntry create(ListenEntry listenEntry, IPPort src, IPPort dst, long seq) {
        var map = connectionEntries.computeIfAbsent(dst, x -> new HashMap<>());
        TcpEntry entry = new TcpEntry(listenEntry, src, dst, seq);
        var old = map.put(src, entry);
        if (old != null) {
            Logger.error(LogType.IMPROPER_USE, "found old connection " + old + " but a new connection with the same tuple is created");
            old.destroy();
        }
        return entry;
    }

    public ListenEntry listen(IPPort dst, ListenHandler handler) {
        ListenEntry entry = new ListenEntry(dst, handler);
        var old = listenEntries.put(dst, entry);
        if (old != null) {
            Logger.error(LogType.IMPROPER_USE, "found old listening entry " + old + " but trying to listen again");
            old.destroy();
        }
        return entry;
    }

    public void removeListen(IPPort dst) {
        listenEntries.remove(dst);
    }

    public void remove(IPPort src, IPPort dst) {
        var map = connectionEntries.get(dst);
        if (map == null) {
            return;
        }
        map.remove(src);
        if (map.isEmpty()) {
            connectionEntries.remove(dst);
        }
    }
}
