package vproxy.vpacket.conntrack.tcp;

import vproxy.vfd.IPPort;

import java.util.LinkedList;

public class TcpListenEntry {
    public static final int MAX_SYN_BACKLOG_SIZE = 128;
    public static final int MAX_BACKLOG_SIZE = 128;

    public final IPPort listening;
    public final TcpListenHandler listenHandler;

    public final LinkedList<TcpEntry> synBacklog = new LinkedList<>();
    public final LinkedList<TcpEntry> backlog = new LinkedList<>();

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
}
