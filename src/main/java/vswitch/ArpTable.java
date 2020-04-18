package vswitch;

import vproxy.selector.SelectorEventLoop;
import vproxy.selector.TimerEvent;
import vproxy.util.Timer;
import vswitch.util.MacAddress;

import java.net.InetAddress;
import java.util.*;

public class ArpTable {
    private final SelectorEventLoop loop;
    private int timeout;

    private final Set<ArpEntry> entries = new HashSet<>();
    private final Map<InetAddress, ArpEntry> ipMap = new HashMap<>();

    public ArpTable(SelectorEventLoop loop, int timeout) {
        this.loop = loop;
        this.timeout = timeout;
    }

    public void record(MacAddress mac, InetAddress ip) {
        var entry = ipMap.get(ip);
        if (entry != null && entry.mac.equals(mac)) {
            entry.resetTimer();
            return;
        }
        entry = new ArpEntry(mac, ip);
        entry.record();
    }

    public MacAddress lookup(InetAddress ip) {
        var entry = ipMap.get(ip);
        if (entry == null) {
            return null;
        }
        return entry.mac;
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

    public void clear() {
        var entries = new HashSet<>(this.entries);
        for (var entry : entries) {
            entry.cancel();
        }
    }

    public class ArpEntry extends Timer {
        public final MacAddress mac;
        public final InetAddress ip;
        private TimerEvent timer;

        private ArpEntry(MacAddress mac, InetAddress ip) {
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
            resetTimer();
        }

        @Override
        public void cancel() {
            entries.remove(this);
            ipMap.remove(ip);
        }
    }
}
