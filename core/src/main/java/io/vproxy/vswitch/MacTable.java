package io.vproxy.vswitch;

import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Timer;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vswitch.iface.Iface;
import io.vproxy.vswitch.iface.XDPIface;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MacTable {
    public static final int MAC_TRY_TO_REFRESH_CACHE_BEFORE_TTL_TIME = 60 * 1000;

    private final SwitchDelegate swCtx;
    private SelectorEventLoop loop;
    private final VirtualNetwork net;
    private int timeout;

    private final Set<MacEntry> entries = new HashSet<>();
    private final Map<MacAddress, MacEntry> macMap = new HashMap<>();
    private final Map<Iface, Set<MacEntry>> ifaceMap = new HashMap<>();

    public MacTable(SwitchDelegate swCtx, SelectorEventLoop loop, VirtualNetwork net, int timeout) {
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
        private int offloadedCount = 0;

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

            tryOffload();

            Logger.trace(LogType.ALERT, "mac entry " + iface.name() + " -> " + mac + " recorded");
        }

        private void tryOffload() {
            if (!(iface instanceof XDPIface xdpIface) || !xdpIface.params.offload()) {
                return;
            }
            if (xdpIface.params.bpf().mac2port() == null) {
                return;
            }
            if (xdpIface.params.bpf().srcmac2count() == null) {
                return;
            }

            try {
                offloadedCount = xdpIface.params.bpf().srcmac2count().getInt(mac);
            } catch (FileNotFoundException e) {
                offloadedCount = 0;
            } catch (IOException e) {
                Logger.error(LogType.SYS_ERROR, "failed to get srcmac2countMap[" + mac + "] for " + xdpIface.name(), e);
                return;
            }

            var mac2portMap = xdpIface.params.bpf().mac2port();
            if (mac2portMap == null) {
                return;
            }
            try {
                mac2portMap.putNetif(mac, xdpIface.nic);
                Logger.trace(LogType.ALERT, "mac entry " + iface.name() + " -> " + mac + " recorded into xdp offload mac map");
            } catch (IOException e) {
                Logger.warn(LogType.SYS_ERROR, "failed to put " + iface.name() + " -> " + mac + " into xdp offload mac map", e);
                return;
            }
            offloaded = true;
        }

        @Override
        public void cancel() {
            super.cancel();

            if (offloaded) {
                if (hasOffloadedPacketPassed()) {
                    start();
                    return;
                }
            }

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

            clearOffload();
        }

        private boolean hasOffloadedPacketPassed() {
            assert iface instanceof XDPIface;
            assert ((XDPIface) iface).params.bpf().srcmac2count() != null;

            try {
                int n = ((XDPIface) iface).params.bpf().srcmac2count().getInt(mac);
                assert Logger.lowLevelDebug("fetched packet count from xdp offload map: " + n + ", last recorded: " + offloadedCount);
                if (n != offloadedCount) {
                    offloadedCount = n;
                    return true;
                }
            } catch (FileNotFoundException ignore) {
                assert Logger.lowLevelDebug("packet count from xdp offload map is missing");
                // fallthrough
            } catch (IOException e) {
                Logger.error(LogType.SYS_ERROR, "failed to get packet count from xdp offload map: " + iface.name() + " -> " + mac, e);
                // fallthrough
            }
            return false;
        }

        private void clearOffload() {
            if (!offloaded) {
                return;
            }
            if (!(iface instanceof XDPIface xdpIface)) {
                return;
            }
            if (xdpIface.params.bpf().mac2port() == null) {
                return;
            }
            var mac2portMap = xdpIface.params.bpf().mac2port();
            try {
                mac2portMap.remove(mac);
                Logger.trace(LogType.ALERT, "mac entry removed from xdp offload mac map: " + iface.name() + " -> " + mac);
            } catch (IOException e) {
                Logger.error(LogType.SYS_ERROR, "failed to removed mac from xdp offload mac map: " + iface.name() + " -> " + mac);
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
