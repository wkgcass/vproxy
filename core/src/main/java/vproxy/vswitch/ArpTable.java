package vproxy.vswitch;

import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.util.Timer;
import vproxy.vfd.IP;
import vproxy.vfd.MacAddress;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ArpTable {
    public static final int ARP_REFRESH_CACHE_BEFORE_TTL_TIME = 60 * 1000;

    private SelectorEventLoop loop;
    private int timeout;

    private final Set<ArpEntry> entries = new HashSet<>();
    private final Map<IP, ArpEntry> ipMap = new HashMap<>();
    private final Map<MacAddress, Set<ArpEntry>> macMap = new HashMap<>();

    public ArpTable(SelectorEventLoop loop, int timeout) {
        this.loop = loop;
        this.timeout = timeout;
    }

    public void record(MacAddress mac, IP ip) {
        var entry = ipMap.get(ip);
        if (entry != null && entry.mac.equals(mac)) {
            entry.resetTimer();
            return;
        }
        entry = new ArpEntry(mac, ip);
        entry.record();
    }

    public MacAddress lookup(IP ip) {
        var entry = ipMap.get(ip);
        if (entry == null) {
            return null;
        }
        return entry.mac;
    }

    public Set<ArpEntry> lookupByMac(MacAddress mac) {
        return macMap.get(mac);
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
        for (var entry : entries) {
            entry.setTimeout(timeout);
        }
    }

    public void setLoop(SelectorEventLoop loop) {
        this.loop = loop;
    }

    public void clearCache() {
        var entries = new HashSet<>(this.entries);
        for (var entry : entries) {
            entry.cancel();
        }
    }

    public Set<ArpEntry> listEntries() {
        return entries;
    }

    public class ArpEntry extends Timer {
        public final MacAddress mac;
        public final IP ip;

        private ArpEntry(MacAddress mac, IP ip) {
            super(ArpTable.this.loop, timeout);
            this.mac = mac;
            this.ip = ip;
        }

        void record() {
            if (ipMap.containsKey(ip)) {
                ArpEntry entry = ipMap.get(ip);
                entry.cancel();
            }
            entries.add(this);
            ipMap.put(ip, this);
            var set = macMap.get(mac);
            //noinspection Java8MapApi
            if (set == null) {
                set = new HashSet<>();
                macMap.put(mac, set);
            }
            set.add(this);
            resetTimer();
        }

        @Override
        public void cancel() {
            super.cancel();

            entries.remove(this);
            var entry = ipMap.remove(ip);
            if (entry != null) {
                var set = macMap.get(entry.mac);
                if (set != null) {
                    set.remove(this);
                    if (set.isEmpty()) {
                        macMap.remove(entry.mac);
                    }
                }
            }
        }

        @Override
        public String toString() {
            return "ArpEntry{" +
                "mac=" + mac +
                ", ip=" + ip +
                '}';
        }
    }

    @Override
    public String toString() {
        return "ArpTable{" + entries + '}';
    }
}
