package io.vproxy.vpacket.conntrack.tcp;

import io.vproxy.base.Config;
import io.vproxy.vfd.IPPort;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TcpListenEntry {
    public static final int MAX_SYN_BACKLOG_SIZE = 65536;
    public static final int MAX_BACKLOG_SIZE = 65536;
    public static final int SYN_BACKLOG_TIMEOUT_MS = 30_000;

    public final IPPort listening;
    public final TcpListenHandler listenHandler;

    public final Set<TcpEntry> synBacklog = new HashSet<>();
    public final ArrayDeque<TcpEntry> backlog = new ArrayDeque<>();

    public TcpListenEntry(IPPort listening, TcpListenHandler handler) {
        this.listening = listening;
        this.listenHandler = handler;
    }

    public void destroy() {
        for (TcpEntry e : backlog) {
            e.destroy();
        }
        for (TcpEntry e : synBacklog) {
            e.destroy();
        }
        backlog.clear();
        synBacklog.clear();
    }

    /**
     * Remove and return entries that have been in the SYN backlog longer than SYN_BACKLOG_TIMEOUT_MS.
     * The caller is responsible for removing these entries from conntrack.
     */
    public List<TcpEntry> cleanupTimedOutSynBacklogEntries() {
        long now = Config.currentTimestamp;
        List<TcpEntry> timedOut = new ArrayList<>();
        var it = synBacklog.iterator();
        while (it.hasNext()) {
            TcpEntry e = it.next();
            if (now - e.synBacklogTimestamp >= SYN_BACKLOG_TIMEOUT_MS) {
                timedOut.add(e);
                it.remove();
            }
        }
        return timedOut;
    }

    @Override
    public String toString() {
        return "TcpListenEntry{" +
            "listening=" + listening +
            '}';
    }

    public String description() {
        return "listen=" + listening;
    }
}
