package io.vproxy.vswitch;

import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Timer;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vswitch.iface.Iface;
import io.vproxy.vswitch.iface.XDPIface;
import io.vproxy.xdp.XDPSocket;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MacTable {
    public static final int MAC_TRY_TO_REFRESH_CACHE_BEFORE_TTL_TIME = 60 * 1000;

    private final SwitchContext swCtx;
    private SelectorEventLoop loop;
    private final VirtualNetwork net;
    private int timeout;

    private final Set<MacEntry> entries = new HashSet<>();
    private final Map<MacAddress, MacEntry> macMap = new HashMap<>();
    private final Map<Iface, Set<MacEntry>> ifaceMap = new HashMap<>();

    public MacTable(SwitchContext swCtx, SelectorEventLoop loop, VirtualNetwork net, int timeout) {
        this.swCtx = swCtx;
        this.loop = loop;
        this.net = net;
        this.timeout = timeout;
    }

    public void record(MacAddress mac, Iface iface) {
        record(mac, iface, false);
    }

    public void record(MacAddress mac, Iface iface, boolean persist) {
        var entry = macMap.get(mac);
        if (entry != null && entry.iface.equals(iface)) {
            if (persist) {
                if (entry.getTimeout() == -1) {
                    return;
                } else {
                    entry.cancel();
                }
            } else {
                entry.resetTimer();
                return;
            }
        }
        // otherwise need to overwrite the entry
        entry = new MacEntry(mac, iface, persist);
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

    public void setLoop(SelectorEventLoop loop) {
        this.loop = loop;
    }

    public void clearCache() {
        var entries = new HashSet<>(this.entries);
        for (var entry : entries) {
            entry.cancel();
        }
    }

    public Set<MacEntry> listEntries() {
        return entries;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
        loop.runOnLoop(() -> {
            for (var entry : entries) {
                entry.setTimeout(timeout);
            }
        });
    }

    public void remove(MacAddress mac) {
        var entry = macMap.get(mac);
        if (entry == null) {
            return;
        }
        entry.cancel();
    }

    public class MacEntry extends Timer {
        public final MacAddress mac;
        public final Iface iface;
        private boolean offloaded = false;

        MacEntry(MacAddress mac, Iface iface, boolean persist) {
            super(MacTable.this.loop, persist ? -1 : timeout);
            this.mac = mac;
            this.iface = iface;
        }

        void record() {
            if (macMap.containsKey(mac)) {
                // the mac is already registered on another iface
                // remove that iface
                MacEntry entry = macMap.get(mac);
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

            tryRecordMacMap();

            Logger.trace(LogType.ALERT, "mac entry " + iface.name() + " -> " + mac + " recorded");
        }

        private void tryRecordMacMap() {
            if (!(iface instanceof XDPIface) || !((XDPIface) iface).offload) {
                return;
            }
            XDPSocket xsk = ((XDPIface) iface).getXsk();
            if (xsk == null) {
                return;
            }
            for (Iface iface : swCtx.getIfaces()) {
                if (iface.getLocalSideVni(net.vni) != net.vni) {
                    continue;
                }
                if (!(iface instanceof XDPIface)) {
                    continue;
                }
                XDPIface xdp = (XDPIface) iface;
                if (xdp.macMap == null) {
                    continue;
                }
                offloaded = true;
                try {
                    xdp.macMap.put(mac, xsk);
                    Logger.trace(LogType.ALERT, "mac entry offloaded into " + xdp.name() + ": " + mac + " -> " + xsk.nic);
                } catch (IOException e) {
                    Logger.error(LogType.SYS_ERROR, "failed to record into " + xdp.name() + ": " + mac + " -> " + xsk.nic);
                }
            }

            if (offloaded) { // make timeout longer
                setTimeout(Math.max(3600_000, timeout));
            }
        }

        @Override
        public void cancel() {
            super.cancel();

            Logger.trace(LogType.ALERT, "mac entry " + iface.name() + " -> " + mac + " removed");

            entries.remove(this);
            macMap.remove(mac);
            var set = ifaceMap.get(iface);
            if (set != null) {
                set.remove(this);
                if (set.isEmpty()) {
                    ifaceMap.remove(iface);
                }
            }

            tryRemoveMacMap();
        }

        private void tryRemoveMacMap() {
            if (!offloaded) {
                return;
            }
            for (Iface iface : swCtx.getIfaces()) {
                if (!(iface instanceof XDPIface)) {
                    continue;
                }
                XDPIface xdp = (XDPIface) iface;
                if (xdp.macMap == null) {
                    continue;
                }
                try {
                    xdp.macMap.remove(mac);
                    Logger.trace(LogType.ALERT, "mac entry removed from " + xdp.name() + ": " + mac);
                } catch (IOException e) {
                    Logger.error(LogType.SYS_ERROR, "failed to removed from " + xdp.name() + ": " + mac);
                }
            }
        }

        @Override
        public void resetTimer() {
            if (getTimeout() == -1) {
                return;
            }
            super.resetTimer();
        }

        public boolean isOffloaded() {
            return offloaded;
        }

        @Override
        public String toString() {
            return "MacEntry{" +
                "mac=" + mac +
                ", iface=" + iface.name() +
                ", offloaded=" + offloaded +
                '}';
        }
    }

    @Override
    public String toString() {
        return "MacTable{" + entries + '}';
    }
}
