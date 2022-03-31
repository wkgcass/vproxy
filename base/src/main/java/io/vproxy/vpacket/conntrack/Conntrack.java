package io.vproxy.vpacket.conntrack;

import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.IPv4;
import io.vproxy.vpacket.AbstractIpPacket;
import io.vproxy.vpacket.TcpPacket;
import io.vproxy.vpacket.UdpPacket;
import io.vproxy.vpacket.conntrack.tcp.TcpEntry;
import io.vproxy.vpacket.conntrack.tcp.TcpListenEntry;
import io.vproxy.vpacket.conntrack.tcp.TcpListenHandler;
import io.vproxy.vpacket.conntrack.udp.UdpEntry;
import io.vproxy.vpacket.conntrack.udp.UdpListenEntry;
import io.vproxy.vpacket.conntrack.udp.UdpListenHandler;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class Conntrack {
    public final SelectorEventLoop loop;

    private final Map<IPPort, TcpListenEntry> tcpListenEntries = new HashMap<>();
    private final Map<IPPort, UdpListenEntry> udpListenEntries = new HashMap<>();
    // dstIPPort => srcIPPort => TcpEntry
    // make dstIPPort to be the first key for better performance
    private final Map<IPPort, Map<IPPort, TcpEntry>> tcpEntries = new HashMap<>();
    private final Map<IPPort, Map<IPPort, UdpEntry>> udpEntries = new HashMap<>();

    private static final IP ipv4BindAny = IP.from("0.0.0.0");
    private static final IP ipv6BindAny = IP.from("::");

    public Conntrack(SelectorEventLoop loop) {
        this.loop = loop;
    }

    public int countTcpListenEntry() {
        return tcpListenEntries.size();
    }

    public Collection<TcpListenEntry> listTcpListenEntries() {
        return tcpListenEntries.values();
    }

    public int countUdpListenEntry() {
        return udpListenEntries.size();
    }

    public Collection<UdpListenEntry> listUdpListenEntries() {
        return udpListenEntries.values();
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

    public int countUdpEntries() {
        int total = 0;
        for (var map : udpEntries.values()) {
            total += map.size();
        }
        return total;
    }

    public Collection<UdpEntry> listUdpEntries() {
        List<UdpEntry> ls = new LinkedList<>();
        for (var map : udpEntries.values()) {
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

    public TcpEntry lookupTcp(AbstractIpPacket ip, TcpPacket tcp) {
        return lookupTcp(new IPPort(ip.getSrc(), tcp.getSrcPort()), new IPPort(ip.getDst(), tcp.getDstPort()));
    }

    public TcpEntry lookupTcp(IPPort remote, IPPort local) {
        var map = tcpEntries.get(local);
        if (map == null) {
            return null;
        }
        return map.get(remote);
    }

    public UdpEntry lookupUdp(AbstractIpPacket ip, UdpPacket udp) {
        return lookupUdp(new IPPort(ip.getSrc(), udp.getSrcPort()), new IPPort(ip.getDst(), udp.getDstPort()));
    }

    public UdpEntry lookupUdp(IPPort remote, IPPort local) {
        var map = udpEntries.get(local);
        if (map == null) {
            return null;
        }
        return map.get(remote);
    }

    protected TcpEntry createTcpEntry(TcpListenEntry listenEntry, IPPort remote, IPPort local, long seq) {
        return new TcpEntry(listenEntry, remote, local, seq);
    }

    protected TcpEntry createTcpEntry(IPPort remote, IPPort local) {
        return new TcpEntry(remote, local);
    }

    protected UdpEntry createUdpEntry(IPPort remote, IPPort local) {
        return new UdpEntry(remote, local);
    }

    private TcpEntry createTcp(IPPort remote, IPPort local, BiFunction<IPPort, IPPort, TcpEntry> constructor) {
        var map = tcpEntries.computeIfAbsent(local, x -> new HashMap<>());
        TcpEntry entry = constructor.apply(remote, local);
        var old = map.put(remote, entry);
        if (old != null) {
            Logger.error(LogType.IMPROPER_USE, "found old connection " + old + " but a new connection with the same tuple is created");
            old.destroy();
        }
        return entry;
    }

    public TcpEntry createTcp(TcpListenEntry listenEntry, IPPort remote, IPPort local, long seq) {
        return createTcp(remote, local, (_src, _dst) -> createTcpEntry(listenEntry, _src, _dst, seq));
    }

    public TcpEntry recordTcp(IPPort remote, IPPort local) {
        return createTcp(remote, local, this::createTcpEntry);
    }

    public UdpEntry recordUdp(IPPort remote, IPPort local) {
        var map = udpEntries.computeIfAbsent(local, x -> new HashMap<>());
        UdpEntry entry = createUdpEntry(remote, local);
        var old = map.put(remote, entry);
        if (old != null) {
            Logger.error(LogType.IMPROPER_USE, "found old udp entry " + old + " but a new udp entry with the same tuple is created");
            old.destroy(false);
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

    public void removeTcp(IPPort remote, IPPort local) {
        var map = tcpEntries.get(local);
        if (map == null) {
            return;
        }
        map.remove(remote);
        if (map.isEmpty()) {
            tcpEntries.remove(local);
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

    public void destroy() {
        for (var entry : listTcpListenEntries()) {
            entry.destroy();
            removeTcpListen(entry.listening);
        }
        for (var entry : listTcpEntries()) {
            entry.destroy();
            removeTcp(entry.remote, entry.local);
        }
        for (var entry : listUdpListenEntries()) {
            entry.destroy();
            removeUdpListen(entry.listening);
        }
        for (var entry : listUdpEntries()) {
            entry.destroy();
            removeUdp(entry.remote, entry.local);
        }
    }
}
