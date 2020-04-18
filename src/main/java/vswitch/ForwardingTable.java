package vswitch;

import vproxy.selector.SelectorEventLoop;
import vproxy.selector.TimerEvent;
import vproxy.util.Timer;
import vswitch.util.Iface;
import vswitch.util.MacAddress;

import java.util.*;

public class ForwardingTable {
    private final SelectorEventLoop loop;
    private int timeout;

    private final Set<ForwardingEntry> entries = new HashSet<>();
    private final Map<MacAddress, ForwardingEntry> macMap = new HashMap<>();
    private final Map<Iface, Set<ForwardingEntry>> ifaceMap = new HashMap<>();

    public ForwardingTable(SelectorEventLoop loop, int timeout) {
        this.loop = loop;
        this.timeout = timeout;
    }

    public void record(MacAddress mac, Iface iface) {
        var entry = macMap.get(mac);
        if (entry != null && entry.iface.equals(iface)) {
            entry.resetTimer();
            return;
        }
        // otherwise need to overwrite the entry
        entry = new ForwardingEntry(mac, iface);
        entry.record();
    }

    public void disconnect(Iface iface) {
        var set = ifaceMap.get(iface);
        if (set == null) {
            return;
        }
        set = new HashSet<>(set);
        for (var entry : set) {
            entry.cancel();
        }
    }

    public Iface lookup(MacAddress mac) {
        var entry = macMap.get(mac);
        if (entry == null) {
            return null;
        }
        return entry.iface;
    }

    public void clear() {
        var entries = new HashSet<>(this.entries);
        for (var entry : entries) {
            entry.cancel();
        }
    }

    public Set<ForwardingEntry> listEntries() {
        return entries;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
        loop.runOnLoop(() -> {
            for (var entry : entries) {
                entry.resetTimer();
            }
        });
    }

    public class ForwardingEntry extends Timer {
        public final MacAddress mac;
        public final Iface iface;

        ForwardingEntry(MacAddress mac, Iface iface) {
            super(ForwardingTable.this.loop, timeout);
            this.mac = mac;
            this.iface = iface;
        }

        void record() {
            if (macMap.containsKey(mac)) {
                // the mac is already registered on another iface
                // remove that iface
                ForwardingEntry entry = macMap.get(mac);
                entry.cancel();
            }
            entries.add(this);
            macMap.put(mac, this);
            var set = ifaceMap.get(iface);
            //noinspection Java8MapApi
            if (set == null) {
                set = new HashSet<>();
                ifaceMap.put(iface, set);
            }
            set.add(this);
            resetTimer();
        }

        @Override
        public void cancel() {
            entries.remove(this);
            macMap.remove(mac);
            var set = ifaceMap.get(iface);
            set.remove(this);
            if (set.isEmpty()) {
                ifaceMap.remove(iface);
            }
        }
    }
}
