package io.vproxy.vpacket.conntrack;

import io.vproxy.vpacket.conntrack.tcp.TcpEntry;
import io.vproxy.vpacket.conntrack.tcp.TcpListenEntry;
import io.vproxy.vpacket.conntrack.tcp.TcpListenHandler;
import io.vproxy.vpacket.conntrack.udp.UdpEntry;
import io.vproxy.vpacket.conntrack.udp.UdpListenEntry;
import io.vproxy.vpacket.conntrack.udp.UdpListenHandler;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.IPv4;
import io.vproxy.vpacket.conntrack.tcp.TcpEntry;
import io.vproxy.vpacket.conntrack.tcp.TcpListenEntry;
import io.vproxy.vpacket.conntrack.tcp.TcpListenHandler;
import io.vproxy.vpacket.conntrack.udp.UdpEntry;
import io.vproxy.vpacket.conntrack.udp.UdpListenEntry;
import io.vproxy.vpacket.conntrack.udp.UdpListenHandler;

import java.util.*;
import java.util.function.Supplier;

public class Conntrack {
    private final Map<IPPort, TcpListenEntry> tcpListenEntries = new HashMap<>();
    private final Map<IPPort, UdpListenEntry> udpListenEntries = new HashMap<>();
    // dstIPPort => srcIPPort => TcpEntry
    // make dstIPPort to be the first key for better performance
    private final Map<IPPort, Map<IPPort, TcpEntry>> tcpEntries = new HashMap<>();
    private final Map<IPPort, Map<IPPort, UdpEntry>> udpEntries = new HashMap<>();

    private static final IP ipv4BindAny = IP.from("0.0.0.0");
    private static final IP ipv6BindAny = IP.from("::");

    public int countListenEntry() {
        return tcpListenEntries.size();
    }

    public Collection<TcpListenEntry> listListenEntries() {
        return tcpListenEntries.values();
    }

    public int countTcpEntries() {
        int total = 0;
        for (var map : tcpEntries.values()) {
            total += map.size();
        }
        return total;
    }

    public Collection<TcpEntry> listTcpEntries() {
        List<TcpEntry> ls = new LinkedList<>();
        for (var map : tcpEntries.values()) {
            ls.addAll(map.values());
        }
        return ls;
    }

    public TcpListenEntry lookupTcpListen(IPPort dst) {
        var ret = tcpListenEntries.get(dst);
        if (ret != null) {
            return ret;
        }
        // search for wildcard
        if (dst.getAddress() instanceof IPv4) {
            return tcpListenEntries.get(new IPPort(ipv4BindAny, dst.getPort()));
        } else {
            return tcpListenEntries.get(new IPPort(ipv6BindAny, dst.getPort()));
        }
    }

    public UdpListenEntry lookupUdpListen(IPPort dst) {
        // wildcard is not allowed
        return udpListenEntries.get(dst);
    }

    public TcpEntry lookupTcp(IPPort src, IPPort dst) {
        var map = tcpEntries.get(dst);
        if (map == null) {
            return null;
        }
        return map.get(src);
    }

    public UdpEntry lookupUdp(IPPort remote, IPPort local) {
        var map = udpEntries.get(local);
        if (map == null) {
            return null;
        }
        return map.get(remote);
    }

    protected TcpEntry createTcpEntry(TcpListenEntry listenEntry, IPPort src, IPPort dst, long seq) {
        return new TcpEntry(listenEntry, src, dst, seq);
    }

    public TcpEntry createTcp(TcpListenEntry listenEntry, IPPort src, IPPort dst, long seq) {
        var map = tcpEntries.computeIfAbsent(dst, x -> new HashMap<>());
        TcpEntry entry = createTcpEntry(listenEntry, src, dst, seq);
        var old = map.put(src, entry);
        if (old != null) {
            Logger.error(LogType.IMPROPER_USE, "found old connection " + old + " but a new connection with the same tuple is created");
            old.destroy();
        }
        return entry;
    }

    public UdpEntry recordUdp(IPPort remote, IPPort local, Supplier<UdpEntry> entrySupplier) {
        var map = udpEntries.computeIfAbsent(local, x -> new HashMap<>());
        var old = map.get(remote);
        if (old == null) {
            old = entrySupplier.get();
            map.put(remote, old);
        } else {
            old.update();
        }
        return old;
    }

    public TcpListenEntry listenTcp(IPPort dst, TcpListenHandler handler) {
        TcpListenEntry entry = new TcpListenEntry(dst, handler);
        var old = tcpListenEntries.put(dst, entry);
        if (old != null) {
            Logger.error(LogType.IMPROPER_USE, "found old listening entry " + old + " but trying to listen again");
            old.destroy();
        }
        return entry;
    }

    public UdpListenEntry listenUdp(IPPort dst, UdpListenHandler handler) {
        UdpListenEntry entry = new UdpListenEntry(dst, handler);
        var old = udpListenEntries.put(dst, entry);
        if (old != null) {
            Logger.error(LogType.IMPROPER_USE, "found old listening entry " + old + " but trying to listen again");
            old.destroy();
        }
        return entry;
    }

    public void removeTcpListen(IPPort dst) {
        tcpListenEntries.remove(dst);
    }

    public void removeUdpListen(IPPort dst) {
        udpListenEntries.remove(dst);
        // remove udp entries as well
        var map = udpEntries.remove(dst);
        if (map != null) {
            for (var x : map.values()) {
                x.destroy();
            }
        }
    }

    public void removeTcp(IPPort src, IPPort dst) {
        var map = tcpEntries.get(dst);
        if (map == null) {
            return;
        }
        map.remove(src);
        if (map.isEmpty()) {
            tcpEntries.remove(dst);
        }
    }

    public void removeUdp(IPPort remote, IPPort local) {
        var map = udpEntries.get(local);
        if (map == null) {
            return;
        }
        map.remove(remote);
        if (map.isEmpty()) {
            udpEntries.remove(local);
        }
    }
}
